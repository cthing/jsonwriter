/*
 * Copyright 2024 C Thing Software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cthing.jsonwriter;

import java.io.IOException;
import java.io.StringWriter;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.params.provider.Arguments.arguments;


@SuppressWarnings({ "UnnecessaryUnicodeEscape", "UnqualifiedFieldAccess" })
public class JsonWriterTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
                                                         .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                                                         .enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
                                                         .build();

    private StringWriter stringWriter;
    private JsonWriter jsonWriter;

    @BeforeEach
    public void setup() {
        this.stringWriter = new StringWriter();
        this.jsonWriter = new JsonWriter(this.stringWriter);
    }

    @Test
    public void testDefaults() {
        assertThat(jsonWriter.isPrettyPrint()).isFalse();
        assertThat(jsonWriter.getIndent()).isEqualTo(4);
        assertThat(jsonWriter.isWriteNullMembers()).isFalse();
    }

    public static Stream<Arguments> escapedNonAsciiProvider() {
        return Stream.of(
                arguments("", "\"\""),
                arguments("   ", "\"   \""),
                arguments("Hello World", "\"Hello World\""),
                arguments("Hello World\n", "\"Hello World\\n\""),
                arguments("Hello World\r\n", "\"Hello World\\r\\n\""),
                arguments("Hello\tWorld", "\"Hello\\tWorld\""),
                arguments("Hello World\f", "\"Hello World\\f\""),
                arguments("Hello World\b", "\"Hello World\\b\""),
                arguments("Hello \"World\"", "\"Hello \\\"World\\\"\""),
                arguments("https://www.cthing.com/foo", "\"https:\\/\\/www.cthing.com\\/foo\""),
                arguments("This \\ That", "\"This \\\\ That\""),
                arguments("Hello \u1E80orld", "\"Hello \\u1E80orld\""),
                arguments("Hello \uD834\uDD1E", "\"Hello \\uD834\\uDD1E\"")
        );
    }

    @ParameterizedTest
    @MethodSource("escapedNonAsciiProvider")
    public void testWriteEscapedStringNonAscii(final String input, final String output) throws IOException {
        jsonWriter.setEscapeNonAscii(true);
        jsonWriter.writeEscapedString(input);
        assertThat(stringWriter).hasToString(output);
    }

    public static Stream<Arguments> escapedAsciiProvider() {
        return Stream.of(
                arguments("", "\"\""),
                arguments("   ", "\"   \""),
                arguments("Hello World", "\"Hello World\""),
                arguments("Hello World\n", "\"Hello World\\n\""),
                arguments("Hello World\r\n", "\"Hello World\\r\\n\""),
                arguments("Hello\tWorld", "\"Hello\\tWorld\""),
                arguments("Hello World\f", "\"Hello World\\f\""),
                arguments("Hello World\b", "\"Hello World\\b\""),
                arguments("Hello \"World\"", "\"Hello \\\"World\\\"\""),
                arguments("https://www.cthing.com/foo", "\"https:\\/\\/www.cthing.com\\/foo\""),
                arguments("This \\ That", "\"This \\\\ That\""),
                arguments("Hello \u1E80orld", "\"Hello \u1E80orld\""),
                arguments("Hello \uD834\uDD1E", "\"Hello \uD834\uDD1E\"")
        );
    }

    @ParameterizedTest
    @MethodSource("escapedAsciiProvider")
    public void testWriteEscapedString(final String input, final String output) throws IOException {
        jsonWriter.writeEscapedString(input);
        assertThat(stringWriter).hasToString(output);
    }

    @Nested
    class NonPrettyPrint {

        @Test
        public void testEmptyObject() throws IOException {
            jsonWriter.startObject()
                      .endObject();
            assertThat(stringWriter.toString()).isEqualTo("{}");
            validate();
        }

        @Test
        public void testEmptyArray() throws IOException {
            jsonWriter.startArray()
                      .endArray();
            assertThat(stringWriter.toString()).isEqualTo("[]");
            validate();
        }

        @Test
        public void testSingleValue() throws IOException {
            jsonWriter.value("Hello World");
            assertThat(stringWriter.toString()).isEqualTo("\"Hello World\"");
            validate();
        }

        @Test
        public void testSimpleObject() throws IOException {
            jsonWriter.startObject()
                      .member("m1", "v1")
                      .member("m2", 2L)
                      .member("m3", 3)
                      .member("m4", (short)4)
                      .member("m5", (byte)5)
                      .member("m6", '6')
                      .member("m7", 7.0F)
                      .member("m8", 8.10)
                      .member("m9", true)
                      .endObject();
            assertThat(stringWriter.toString())
                    .isEqualTo("""
                                {"m1":"v1","m2":2,"m3":3,"m4":4,"m5":5,"m6":"6","m7":7.0,"m8":8.1,"m9":true}""");
            validate();
        }

        @Test
        public void testSimpleArray() throws IOException {
            jsonWriter.startArray()
                      .value("v1")
                      .value(2L)
                      .value(3)
                      .value((short)4)
                      .value((byte)5)
                      .value('6')
                      .value(7.0F)
                      .value(8.10)
                      .value(true)
                      .endArray();
            assertThat(stringWriter.toString()).isEqualTo("""
                                                           ["v1",2,3,4,5,"6",7.0,8.1,true]""");
            validate();
        }

        @Test
        public void testComplexObject() throws IOException {
            jsonWriter.startObject();

            jsonWriter.member("m1", 1);

            jsonWriter.member("m2");
            jsonWriter.startObject();
            jsonWriter.member("m3", "v3");
            jsonWriter.member("m4", "v4");
            jsonWriter.endObject();

            jsonWriter.memberStartObject("m5");
            jsonWriter.member("m6", "v6");
            jsonWriter.member("m7");
            jsonWriter.startArray();
            jsonWriter.value("a");
            jsonWriter.value("b");
            jsonWriter.endArray();
            jsonWriter.endObject();

            jsonWriter.endObject();

            assertThat(stringWriter.toString())
                    .isEqualTo("""
                                {"m1":1,"m2":{"m3":"v3","m4":"v4"},"m5":{"m6":"v6","m7":["a","b"]}}""");
            validate();
        }

        @Test
        public void testComplexArray() throws IOException {
            jsonWriter.startArray();

            jsonWriter.value(1);

            jsonWriter.startArray();
            jsonWriter.value(2);
            jsonWriter.value("3");
            jsonWriter.endArray();

            jsonWriter.startObject();
            jsonWriter.member("m1", "a");
            jsonWriter.member("m2", "b");
            jsonWriter.endObject();

            jsonWriter.endArray();

            assertThat(stringWriter.toString()).isEqualTo("""
                                                           [1,[2,"3"],{"m1":"a","m2":"b"}]""");
            validate();
        }

        @Test
        public void testSkipNulls() throws IOException {
            jsonWriter.setWriteNullMembers(false);
            jsonWriter.startObject()
                      .member("m1", "v1")
                      .member("m2", null)
                      .member("m3", 3)
                      .endObject();
            assertThat(stringWriter.toString()).isEqualTo("""
                                                           {"m1":"v1","m3":3}""");
            validate();
        }

        @Test
        public void testIncludeNulls() throws IOException {
            jsonWriter.setWriteNullMembers(true);
            jsonWriter.startObject();
            jsonWriter.member("m1", "v1");
            jsonWriter.member("m2", null);
            jsonWriter.member("m3", 3);
            jsonWriter.endObject();
            assertThat(stringWriter.toString()).isEqualTo("""
                                                           {"m1":"v1","m2":null,"m3":3}""");
            validate();
        }
    }

    @Nested
    class PrettyPrint {

        @Test
        public void testEmptyObject() throws IOException {
            jsonWriter.setPrettyPrint(true);
            jsonWriter.startObject();
            jsonWriter.endObject();
            assertThat(stringWriter.toString()).isEqualTo("""
                                                           {
                                                           }
                                                           """);
            validate();
        }

        @Test
        public void testEmptyArray() throws IOException {
            jsonWriter.setPrettyPrint(true);
            jsonWriter.startArray();
            jsonWriter.endArray();
            assertThat(stringWriter.toString()).isEqualTo("""
                                                           [
                                                           ]
                                                           """);
            validate();
        }

        @Test
        public void testSingleValue() throws IOException {
            jsonWriter.setPrettyPrint(true);
            jsonWriter.value("Hello World");
            assertThat(stringWriter.toString()).isEqualTo("\"Hello World\"\n");
            validate();
        }

        @Test
        public void testSimpleObject() throws IOException {
            jsonWriter.setPrettyPrint(true);
            jsonWriter.startObject()
                      .member("m1", "v1")
                      .member("m2", 2L)
                      .member("m3", 3)
                      .member("m4", (short)4)
                      .member("m5", (byte)5)
                      .member("m6", '6')
                      .member("m7", 7.0F)
                      .member("m8", 8.10)
                      .member("m9", true)
                      .endObject();
            assertThat(stringWriter.toString()).isEqualTo("""
                                                          {
                                                              "m1": "v1",
                                                              "m2": 2,
                                                              "m3": 3,
                                                              "m4": 4,
                                                              "m5": 5,
                                                              "m6": "6",
                                                              "m7": 7.0,
                                                              "m8": 8.1,
                                                              "m9": true
                                                          }
                                                          """);
            validate();
        }

        @Test
        public void testSimpleArray() throws IOException {
            jsonWriter.setPrettyPrint(true);
            jsonWriter.startArray();
            jsonWriter.value("v1");
            jsonWriter.value(2L);
            jsonWriter.value(3);
            jsonWriter.value((short)4);
            jsonWriter.value((byte)5);
            jsonWriter.value('6');
            jsonWriter.value(7.0F);
            jsonWriter.value(8.10);
            jsonWriter.value(true);
            jsonWriter.endArray();
            assertThat(stringWriter.toString()).isEqualTo("""
                                                           [
                                                               "v1",
                                                               2,
                                                               3,
                                                               4,
                                                               5,
                                                               "6",
                                                               7.0,
                                                               8.1,
                                                               true
                                                           ]
                                                           """);
            validate();
        }

        @Test
        public void testComplexObject() throws IOException {
            jsonWriter.setPrettyPrint(true);
            jsonWriter.startObject();

            jsonWriter.member("m1", 1);

            jsonWriter.member("m2")
                      .startObject()
                      .member("m3", "v3")
                      .member("m4", "v4")
                      .endObject();

            jsonWriter.memberStartObject("m5");
            jsonWriter.member("m6", "v6");
            jsonWriter.memberStartArray("m7");
            jsonWriter.value("a");
            jsonWriter.value("b");
            jsonWriter.endArray();
            jsonWriter.endObject();

            jsonWriter.endObject();

            assertThat(stringWriter.toString()).isEqualTo("""
                                                          {
                                                              "m1": 1,
                                                              "m2": {
                                                                  "m3": "v3",
                                                                  "m4": "v4"
                                                              },
                                                              "m5": {
                                                                  "m6": "v6",
                                                                  "m7": [
                                                                      "a",
                                                                      "b"
                                                                  ]
                                                              }
                                                          }
                                                          """);
            validate();
        }

        @Test
        public void testComplexArray() throws IOException {
            jsonWriter.setPrettyPrint(true);
            jsonWriter.startArray();

            jsonWriter.value(1);

            jsonWriter.startArray();
            jsonWriter.value(2);
            jsonWriter.value("3");
            jsonWriter.endArray();

            jsonWriter.startObject();
            jsonWriter.member("m1", "a");
            jsonWriter.member("m2", "b");
            jsonWriter.endObject();

            jsonWriter.endArray();

            assertThat(stringWriter.toString()).isEqualTo("""
                                                          [
                                                              1,
                                                              [
                                                                  2,
                                                                  "3"
                                                              ],
                                                              {
                                                                  "m1": "a",
                                                                  "m2": "b"
                                                              }
                                                          ]
                                                          """);
            validate();
        }

        @Test
        public void testChangedIndent() throws IOException {
            jsonWriter.setPrettyPrint(true);
            jsonWriter.setIndent(2);
            jsonWriter.startArray();

            jsonWriter.value(1);

            jsonWriter.startArray();
            jsonWriter.value(2);
            jsonWriter.value("3");
            jsonWriter.endArray();

            jsonWriter.startObject();
            jsonWriter.member("m1", "a");
            jsonWriter.member("m2", "b");
            jsonWriter.endObject();

            jsonWriter.endArray();

            assertThat(stringWriter.toString()).isEqualTo("""
                                                          [
                                                            1,
                                                            [
                                                              2,
                                                              "3"
                                                            ],
                                                            {
                                                              "m1": "a",
                                                              "m2": "b"
                                                            }
                                                          ]
                                                          """);
            validate();
        }

        @Test
        public void testSkipNulls() throws IOException {
            jsonWriter.setPrettyPrint(true);
            jsonWriter.setWriteNullMembers(false);
            jsonWriter.startObject();
            jsonWriter.member("m1", "v1");
            jsonWriter.member("m2", null);
            jsonWriter.member("m3", 3);
            jsonWriter.endObject();
            assertThat(stringWriter.toString()).isEqualTo("""
                                                          {
                                                              "m1": "v1",
                                                              "m3": 3
                                                          }
                                                          """);
            validate();
        }

        @Test
        public void testIncludeNulls() throws IOException {
            jsonWriter.setPrettyPrint(true);
            jsonWriter.setWriteNullMembers(true);
            jsonWriter.startObject();
            jsonWriter.member("m1", "v1");
            jsonWriter.member("m2", null);
            jsonWriter.member("m3", 3);
            jsonWriter.endObject();
            assertThat(stringWriter.toString()).isEqualTo("""
                                                          {
                                                              "m1": "v1",
                                                              "m2": null,
                                                              "m3": 3
                                                          }
                                                          """);
            validate();
        }

        @Test
        public void testCombinations1() throws IOException {
            jsonWriter.setPrettyPrint(true);
            jsonWriter.startArray();

            jsonWriter.value("a");

            jsonWriter.startObject();
            jsonWriter.member("m2", 2);
            jsonWriter.endObject();

            jsonWriter.startObject();
            jsonWriter.member("m3", 3);
            jsonWriter.endObject();

            jsonWriter.startArray();
            jsonWriter.value(4);
            jsonWriter.endArray();

            jsonWriter.startArray();
            jsonWriter.value(5);
            jsonWriter.endArray();

            jsonWriter.startObject();
            jsonWriter.member("m6", 6);
            jsonWriter.endObject();

            jsonWriter.endArray();

            assertThat(stringWriter.toString()).isEqualTo("""
                                                          [
                                                              "a",
                                                              {
                                                                  "m2": 2
                                                              },
                                                              {
                                                                  "m3": 3
                                                              },
                                                              [
                                                                  4
                                                              ],
                                                              [
                                                                  5
                                                              ],
                                                              {
                                                                  "m6": 6
                                                              }
                                                          ]
                                                          """);
            validate();
        }

        @Test
        public void testCombinations2() throws IOException {
            jsonWriter.setPrettyPrint(true);
            jsonWriter.startArray();

            jsonWriter.startObject();
            jsonWriter.member("m1", 1);
            jsonWriter.endObject();

            jsonWriter.endArray();

            assertThat(stringWriter.toString()).isEqualTo("""
                                                          [
                                                              {
                                                                  "m1": 1
                                                              }
                                                          ]
                                                          """);
            validate();
        }

        @Test
        public void testCombinations3() throws IOException {
            jsonWriter.setPrettyPrint(true);
            jsonWriter.startArray();

            jsonWriter.startArray();
            jsonWriter.value(1);
            jsonWriter.endArray();

            jsonWriter.endArray();

            assertThat(stringWriter.toString()).isEqualTo("""
                                                          [
                                                              [
                                                                  1
                                                              ]
                                                          ]
                                                          """);
            validate();
        }

        @Test
        public void testCombinations4() throws IOException {
            jsonWriter.setPrettyPrint(true);
            jsonWriter.startObject();

            jsonWriter.member("m1");
            jsonWriter.startObject();
            jsonWriter.member("m2", 2);
            jsonWriter.endObject();

            jsonWriter.member("m3", 3);

            jsonWriter.endObject();

            assertThat(stringWriter.toString()).isEqualTo("""
                                                          {
                                                              "m1": {
                                                                  "m2": 2
                                                              },
                                                              "m3": 3
                                                          }
                                                          """);
            validate();
        }

        @Test
        public void testCombinations5() throws IOException {
            jsonWriter.setPrettyPrint(true);
            jsonWriter.startArray();

            jsonWriter.startObject();
            jsonWriter.member("m1", 1);
            jsonWriter.endObject();

            jsonWriter.value(2);

            jsonWriter.endArray();

            assertThat(stringWriter.toString()).isEqualTo("""
                                                          [
                                                              {
                                                                  "m1": 1
                                                              },
                                                              2
                                                          ]
                                                          """);
            validate();
        }

        @Test
        public void testCombinations6() throws IOException {
            jsonWriter.setPrettyPrint(true);
            jsonWriter.startObject();

            jsonWriter.member("m1");
            jsonWriter.startArray();
            jsonWriter.value(1);
            jsonWriter.endArray();

            jsonWriter.member("m2", 2);

            jsonWriter.endObject();

            assertThat(stringWriter.toString()).isEqualTo("""
                                                          {
                                                              "m1": [
                                                                  1
                                                              ],
                                                              "m2": 2
                                                          }
                                                          """);
            validate();
        }

        @Test
        public void testCombinations7() throws IOException {
            jsonWriter.setPrettyPrint(true);
            jsonWriter.startObject();

            jsonWriter.member("m1");
            jsonWriter.startArray();
            jsonWriter.value(1);
            jsonWriter.endArray();

            jsonWriter.member("m2");
            jsonWriter.value(2);

            jsonWriter.endObject();

            assertThat(stringWriter.toString()).isEqualTo("""
                                                          {
                                                              "m1": [
                                                                  1
                                                              ],
                                                              "m2": 2
                                                          }
                                                          """);
            validate();
        }

        @Test
        public void testCombinations8() throws IOException {
            jsonWriter.setPrettyPrint(true);
            jsonWriter.startArray();

            jsonWriter.startArray();
            jsonWriter.value(1);
            jsonWriter.endArray();

            jsonWriter.value(2);

            jsonWriter.endArray();

            assertThat(stringWriter.toString()).isEqualTo("""
                                                          [
                                                              [
                                                                  1
                                                              ],
                                                              2
                                                          ]
                                                          """);
            validate();
        }

        @Test
        public void testCombinations9() throws IOException {
            jsonWriter.setPrettyPrint(true);
            jsonWriter.startObject();

            jsonWriter.member("m1");
            jsonWriter.value(1);

            jsonWriter.member("m2", 2);

            jsonWriter.endObject();

            assertThat(stringWriter.toString()).isEqualTo("""
                                                          {
                                                              "m1": 1,
                                                              "m2": 2
                                                          }
                                                          """);
            validate();
        }

        @Test
        public void testCombinations10() throws IOException {
            jsonWriter.setPrettyPrint(true);
            jsonWriter.startObject();

            jsonWriter.member("m1");
            jsonWriter.value(1);

            jsonWriter.member("m2");
            jsonWriter.value(2);

            jsonWriter.endObject();

            assertThat(stringWriter.toString()).isEqualTo("""
                                                          {
                                                              "m1": 1,
                                                              "m2": 2
                                                          }
                                                          """);
            validate();
        }
    }

    @Nested
    class BadCombinations {

        @Test
        public void testNothingToMember() {
            assertThatIllegalStateException().isThrownBy(() -> jsonWriter.member("m1", "v1"));
        }

        @Test
        public void testObjectStartToValue() {
            assertThatIllegalStateException().isThrownBy(() -> {
                jsonWriter.startObject();
                jsonWriter.value("v1");
            });
        }

        @Test
        public void testArrayStartToObjectEnd() {
            assertThatIllegalStateException().isThrownBy(() -> {
                jsonWriter.startArray();
                jsonWriter.endObject();
            });
        }

        @Test
        public void testMemberToObjectStart() {
            assertThatIllegalStateException().isThrownBy(() -> {
                jsonWriter.startObject();
                jsonWriter.member("m1", 1);
                jsonWriter.startObject();
            });
        }

        @Test
        public void testMemberNameToObjectEnd() {
            assertThatIllegalStateException().isThrownBy(() -> {
                jsonWriter.startObject();
                jsonWriter.member("m1");
                jsonWriter.endObject();
            });
        }

        @Test
        public void testStartArrayEndObject() {
            assertThatIllegalStateException().isThrownBy(() -> {
                jsonWriter.startArray();
                jsonWriter.endObject();
            });
        }

        @Test
        public void testStartObjectEndArray() {
            assertThatIllegalStateException().isThrownBy(() -> {
                jsonWriter.startObject();
                jsonWriter.endArray();
            });
        }
    }

    public void validate() {
        assertThatNoException().isThrownBy(() -> MAPPER.readTree(this.stringWriter.toString()));
    }
}
