/*
 * Copyright (C) 2009-2010 Mathias Doenitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.parboiled.transform;

import com.google.common.collect.ImmutableList;
import org.parboiled.BaseParser;
import org.parboiled.Capture;
import org.parboiled.Rule;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

@SuppressWarnings({"InstantiatingObjectToGetClassObject"})
public class ErrorDetectionTest extends TransformationTest {

    private final List<RuleMethodProcessor> processors = ImmutableList.of(
            new UnusedLabelsRemover(),
            new ReturnInstructionUnifier(),
            new InstructionGraphCreator(),
            new ImplicitActionsConverter(),
            new InstructionGroupCreator(),
            new InstructionGroupPreparer(),
            new CaptureClassGenerator(true),
            new ActionClassGenerator(true),
            new RuleMethodRewriter()
    );

    @BeforeClass
    @Override
    public void setup() throws IOException {
        // do not setup with the TestParser
    }

    @Test
    public synchronized void testRuleWithCaptureInAction() throws Exception {
        setup(new BaseParser<Object>() {
            public Rule RuleWithCaptureInAction() {
                return sequence('a', ACTION(5 == CAPTURE(NODES("a").size()).get()));
            }
        }.getClass());
        try {
            processMethod("RuleWithCaptureInAction", processors);
            fail();
        } catch (Exception e) {
            assertEquals(e.getMessage(), "Method 'RuleWithCaptureInAction' contains illegal nesting of " +
                    "ACTION(...) and/or CAPTURE(...) constructs");
        }
    }

    @Test
    public synchronized void testRuleWithActionAccessingPrivateField() throws Exception {
        setup(new BaseParser<Object>() {
            private int privateInt = 5;

            public Rule RuleWithActionAccessingPrivateField() {
                return sequence('a', privateInt == 0);
            }
        }.getClass());

        try {
            processMethod("RuleWithActionAccessingPrivateField", processors);
            fail();
        } catch (Exception e) {
            assertEquals(e.getMessage(),
                    "Rule method 'RuleWithActionAccessingPrivateField' contains an illegal access to private field 'privateInt'.\n" +
                            "Mark the field protected or package-private if you want to prevent public access!");
        }
    }

    @Test
    public synchronized void testRuleWithActionAccessingPrivateMethod() throws Exception {
        setup(new BaseParser<Object>() {
            public Rule RuleWithActionAccessingPrivateMethod() {
                return sequence('a', privateAction());
            }

            private boolean privateAction() {
                return true;
            }
        }.getClass());

        try {
            processMethod("RuleWithActionAccessingPrivateMethod", processors);
            fail();
        } catch (Exception e) {
            assertEquals(e.getMessage(),
                    "Rule method 'RuleWithActionAccessingPrivateMethod' contains an illegal call to private method 'privateAction'.\n" +
                            "Mark 'privateAction' protected or package-private if you want to prevent public access!");
        }
    }

    @Test
    public synchronized void testRuleWithIllegalCapture() throws Exception {
        setup(new BaseParser<Object>() {
            public Rule RuleWithIllegalCapture() {
                Capture<String> capture = CAPTURE(TEXT("a"));
                return sequence('a', 'b', capture.get());
            }
        }.getClass());

        try {
            processMethod("RuleWithIllegalCapture", processors);
            fail();
        } catch (Exception e) {
            assertEquals(e.getMessage(), "Method 'RuleWithIllegalCapture' contains illegal CAPTURE(...) constructs " +
                    "that are not direct arguments to rule creating methods");
        }
    }

}