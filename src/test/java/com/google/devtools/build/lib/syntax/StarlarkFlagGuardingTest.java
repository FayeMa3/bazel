// Copyright 2018 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.syntax;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.syntax.StarlarkSemantics.FlagIdentifier;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Starlark evaluation tests which verify the infrastructure which toggles build API methods and
 * parameters with semantic flags.
 */
@RunWith(JUnit4.class)
public final class StarlarkFlagGuardingTest {

  private EvaluationTestCase ev = new EvaluationTestCase();

  /** Mock containing exposed methods for flag-guarding tests. */
  @StarlarkBuiltin(name = "Mock", doc = "")
  public static class Mock implements StarlarkValue {

    @StarlarkMethod(
        name = "positionals_only_method",
        documented = false,
        parameters = {
          @Param(name = "a", positional = true, named = false, type = Integer.class),
          @Param(
              name = "b",
              positional = true,
              named = false,
              type = Boolean.class,
              enableOnlyWithFlag = FlagIdentifier.EXPERIMENTAL_SIBLING_REPOSITORY_LAYOUT,
              valueWhenDisabled = "False"),
          @Param(name = "c", positional = true, named = false, type = Integer.class),
        },
        useStarlarkThread = true)
    public String positionalsOnlyMethod(Integer a, boolean b, Integer c, StarlarkThread thread) {
      return "positionals_only_method(" + a + ", " + b + ", " + c + ")";
    }

    @StarlarkMethod(
        name = "keywords_only_method",
        documented = false,
        parameters = {
          @Param(name = "a", positional = false, named = true, type = Integer.class),
          @Param(
              name = "b",
              positional = false,
              named = true,
              type = Boolean.class,
              enableOnlyWithFlag = FlagIdentifier.EXPERIMENTAL_SIBLING_REPOSITORY_LAYOUT,
              valueWhenDisabled = "False"),
          @Param(name = "c", positional = false, named = true, type = Integer.class),
        },
        useStarlarkThread = true)
    public String keywordsOnlyMethod(Integer a, boolean b, Integer c, StarlarkThread thread) {
      return "keywords_only_method(" + a + ", " + b + ", " + c + ")";
    }

    @StarlarkMethod(
        name = "mixed_params_method",
        documented = false,
        parameters = {
          @Param(name = "a", positional = true, named = false, type = Integer.class),
          @Param(
              name = "b",
              positional = true,
              named = false,
              type = Boolean.class,
              enableOnlyWithFlag = FlagIdentifier.EXPERIMENTAL_SIBLING_REPOSITORY_LAYOUT,
              valueWhenDisabled = "False"),
          @Param(
              name = "c",
              positional = false,
              named = true,
              type = Integer.class,
              enableOnlyWithFlag = FlagIdentifier.EXPERIMENTAL_SIBLING_REPOSITORY_LAYOUT,
              valueWhenDisabled = "3"),
          @Param(name = "d", positional = false, named = true, type = Boolean.class),
        },
        useStarlarkThread = true)
    public String mixedParamsMethod(
        Integer a, boolean b, Integer c, boolean d, StarlarkThread thread) {
      return "mixed_params_method(" + a + ", " + b + ", " + c + ", " + d + ")";
    }

    @StarlarkMethod(
        name = "keywords_multiple_flags",
        documented = false,
        parameters = {
          @Param(name = "a", positional = false, named = true, type = Integer.class),
          @Param(
              name = "b",
              positional = false,
              named = true,
              type = Boolean.class,
              disableWithFlag = FLAG2,
              valueWhenDisabled = "False"),
          @Param(
              name = "c",
              positional = false,
              named = true,
              type = Integer.class,
              enableOnlyWithFlag = FLAG1,
              valueWhenDisabled = "3"),
        },
        useStarlarkThread = true)
    public String keywordsMultipleFlags(Integer a, boolean b, Integer c, StarlarkThread thread) {
      return "keywords_multiple_flags(" + a + ", " + b + ", " + c + ")";
    }
  }

  // We pick two arbitrary flags (one experimental, one incompatible) for our testing.
  private static final String FLAG1 = FlagIdentifier.EXPERIMENTAL_SIBLING_REPOSITORY_LAYOUT;
  private static final StarlarkSemantics FLAG1_TRUE =
      StarlarkSemantics.DEFAULT.toBuilder().experimentalSiblingRepositoryLayout(true).build();
  private static final StarlarkSemantics FLAG1_FALSE =
      StarlarkSemantics.DEFAULT.toBuilder().experimentalSiblingRepositoryLayout(false).build();

  private static final String FLAG2 = FlagIdentifier.INCOMPATIBLE_NO_ATTR_LICENSE;
  private static final StarlarkSemantics FLAG2_TRUE =
      StarlarkSemantics.DEFAULT.toBuilder().incompatibleNoAttrLicense(true).build();
  private static final StarlarkSemantics FLAG2_FALSE =
      StarlarkSemantics.DEFAULT.toBuilder().incompatibleNoAttrLicense(false).build();

  @Test
  public void testPositionalsOnlyGuardedMethod() throws Exception {
    ev.new Scenario(FLAG1_TRUE)
        .update("mock", new Mock())
        .testEval(
            "mock.positionals_only_method(1, True, 3)", "'positionals_only_method(1, true, 3)'");

    ev.new Scenario(FLAG1_TRUE)
        .update("mock", new Mock())
        .testIfErrorContains(
            "in call to positionals_only_method(), parameter 'b' got value of type 'int', want"
                + " 'bool'",
            "mock.positionals_only_method(1, 3)");

    ev.new Scenario(FLAG1_FALSE)
        .update("mock", new Mock())
        .testEval("mock.positionals_only_method(1, 3)", "'positionals_only_method(1, false, 3)'");

    ev.new Scenario(FLAG1_FALSE)
        .update("mock", new Mock())
        .testIfErrorContains(
            "in call to positionals_only_method(), parameter 'c' got value of type 'bool', want"
                + " 'int'",
            "mock.positionals_only_method(1, True, 3)");
  }

  @Test
  public void testKeywordOnlyGuardedMethod() throws Exception {
    ev.new Scenario(FLAG1_TRUE)
        .update("mock", new Mock())
        .testEval(
            "mock.keywords_only_method(a=1, b=True, c=3)", "'keywords_only_method(1, true, 3)'");

    ev.new Scenario(FLAG1_TRUE)
        .update("mock", new Mock())
        .testIfErrorContains(
            "keywords_only_method() missing 1 required named argument: b",
            "mock.keywords_only_method(a=1, c=3)");

    ev.new Scenario(FLAG1_FALSE)
        .update("mock", new Mock())
        .testEval("mock.keywords_only_method(a=1, c=3)", "'keywords_only_method(1, false, 3)'");

    ev.new Scenario(FLAG1_FALSE)
        .update("mock", new Mock())
        .testIfErrorContains(
            "parameter 'b' is experimental and thus unavailable with the current "
                + "flags. It may be enabled by setting "
                + "--experimental_sibling_repository_layout",
            "mock.keywords_only_method(a=1, b=True, c=3)");
  }

  @Test
  public void testMixedParamsMethod() throws Exception {
    // def mixed_params_method(a, b, c = ?, d = ?)
    ev.new Scenario(FLAG1_TRUE)
        .update("mock", new Mock())
        .testEval(
            "mock.mixed_params_method(1, True, c=3, d=True)",
            "'mixed_params_method(1, true, 3, true)'");

    ev.new Scenario(FLAG1_TRUE)
        .update("mock", new Mock())
        .testIfErrorContains(
            // Missing named arguments (d) are not reported
            // if there are missing positional arguments.
            "mixed_params_method() missing 1 required positional argument: b",
            "mock.mixed_params_method(1, c=3)");

    // def mixed_params_method(a, b disabled = False, c disabled = 3, d = ?)
    ev.new Scenario(FLAG1_FALSE)
        .update("mock", new Mock())
        .testEval(
            "mock.mixed_params_method(1, d=True)", "'mixed_params_method(1, false, 3, true)'");

    ev.new Scenario(FLAG1_FALSE)
        .update("mock", new Mock())
        .testIfErrorContains(
            "mixed_params_method() accepts no more than 1 positional argument but got 2",
            "mock.mixed_params_method(1, True, d=True)");

    ev.new Scenario(FLAG1_FALSE)
        .update("mock", new Mock())
        .testIfErrorContains(
            "mixed_params_method() accepts no more than 1 positional argument but got 2",
            "mock.mixed_params_method(1, True, c=3, d=True)");
  }

  @Test
  public void testKeywordsMultipleFlags() throws Exception {
    StarlarkSemantics tf = FLAG1_TRUE.toBuilder().incompatibleNoAttrLicense(false).build();
    ev.new Scenario(tf)
        .update("mock", new Mock())
        .testEval(
            "mock.keywords_multiple_flags(a=42, b=True, c=0)",
            "'keywords_multiple_flags(42, true, 0)'")
        .testIfErrorContains(
            "keywords_multiple_flags() missing 2 required named arguments: b, c",
            "mock.keywords_multiple_flags(a=42)");

    StarlarkSemantics ft = FLAG1_FALSE.toBuilder().incompatibleNoAttrLicense(true).build();
    ev.new Scenario(ft)
        .update("mock", new Mock())
        .testEval("mock.keywords_multiple_flags(a=42)", "'keywords_multiple_flags(42, false, 3)'")
        .testIfErrorContains(
            "parameter 'b' is deprecated and will be removed soon. It may be "
                + "temporarily re-enabled by setting --incompatible_no_attr_license=false",
            "mock.keywords_multiple_flags(a=42, b=True, c=0)");
  }

  @Test
  public void testExperimentalFlagGuardedValue() throws Exception {
    // This test uses an arbitrary experimental flag to verify this functionality. If this
    // experimental flag were to go away, this test may be updated to use any experimental flag.
    // The flag itself is unimportant to the test.

    // clumsy way to predeclare
    ev =
        new EvaluationTestCase() {
          @Override
          protected Object newModuleHook(ImmutableMap.Builder<String, Object> predeclared) {
            predeclared.put(
                "GlobalSymbol",
                FlagGuardedValue.onlyWhenExperimentalFlagIsTrue(
                    FlagIdentifier.EXPERIMENTAL_SIBLING_REPOSITORY_LAYOUT, "foo"));
            return null; // no client data
          }
        };

    String errorMessage =
        "GlobalSymbol is experimental and thus unavailable with the current "
            + "flags. It may be enabled by setting --experimental_sibling_repository_layout";

    ev.new Scenario(FLAG1_TRUE).setUp("var = GlobalSymbol").testLookup("var", "foo");

    ev.new Scenario(FLAG1_FALSE).testIfErrorContains(errorMessage, "var = GlobalSymbol");

    ev.new Scenario(FLAG1_FALSE)
        .testIfErrorContains(errorMessage, "def my_function():", "  var = GlobalSymbol");

    ev.new Scenario(FLAG1_FALSE)
        .setUp("GlobalSymbol = 'other'", "var = GlobalSymbol")
        .testLookup("var", "other");
  }

  @Test
  public void testIncompatibleFlagGuardedValue() throws Exception {
    // This test uses an arbitrary incompatible flag to verify this functionality. If this
    // incompatible flag were to go away, this test may be updated to use any incompatible flag.
    // The flag itself is unimportant to the test.

    ev =
        new EvaluationTestCase() {
          @Override
          protected Object newModuleHook(ImmutableMap.Builder<String, Object> predeclared) {
            predeclared.put(
                "GlobalSymbol", FlagGuardedValue.onlyWhenIncompatibleFlagIsFalse(FLAG2, "foo"));
            return null; // no client data
          }
        };

    String errorMessage =
        "GlobalSymbol is deprecated and will be removed soon. It may be "
            + "temporarily re-enabled by setting --"
            + FLAG2
            + "=false";

    ev.new Scenario(FLAG2_FALSE).setUp("var = GlobalSymbol").testLookup("var", "foo");

    ev.new Scenario(FLAG2_TRUE).testIfErrorContains(errorMessage, "var = GlobalSymbol");

    ev.new Scenario(FLAG2_TRUE)
        .testIfErrorContains(errorMessage, "def my_function():", "  var = GlobalSymbol");

    ev.new Scenario(FLAG2_TRUE)
        .setUp("GlobalSymbol = 'other'", "var = GlobalSymbol")
        .testLookup("var", "other");
  }
}
