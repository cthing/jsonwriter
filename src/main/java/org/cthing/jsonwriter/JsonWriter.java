/*
 * Copyright 2024 C Thing Software
 * SPDX-License-Identifier: Apache-2.0
 */

package org.cthing.jsonwriter;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.Set;

import org.cthing.annotations.AccessForTesting;
import org.cthing.escapers.JsonEscaper;
import org.jspecify.annotations.Nullable;


/**
 * Writes JSON either with or without pretty printing.
 *
 * <h2>Usage</h2>
 *
 * <p>To begin writing JSON, create an instance of a {@link JsonWriter}. The instance is not thread safe. Naming
 * of the methods follows that used in
 * <a href="https://datatracker.ietf.org/doc/html/rfc8259">IETF RFC-8259: The JavaScript Object Notation (JSON) Data
 * Interchange Format</a>.</p>
 *
 * <h3>Writing an Object</h3>
 *
 * <p>The following are examples of writing a JSON object. Note the use of the fluent API.</p>
 *
 * <p>Simple object with two ways to create a member:</p>
 * <pre>
 * final JsonWriter writer = new JsonWriter();
 * writer.setPrettyPrint(true);
 * writer.startObject()
 *       .member("foo", "bar")
 *       .member("joe", 12)
 *       .member("abc")
 *       .value(true)
 *       .endObject();
 * </pre>
 *
 * <p>JSON written:</p>
 * <pre>
 * {
 *     "foo": "bar",
 *     "joe": 12,
 *     "abc": true
 * }
 * </pre>
 *
 * <p>Two ways to create nested objects:</p>
 * <pre>
 * final JsonWriter writer = new JsonWriter();
 * writer.setPrettyPrint(true);
 * writer.startObject()
 *       .member("abc")
 *       .startObject()
 *       .member("def", "hello")
 *       .endObject()
 *       .memberStartObject("xyz")
 *       .member("hij", 23)
 *       .endObject()
 *       .endObject();
 * </pre>
 *
 * <p>JSON written:</p>
 * <pre>
 * {
 *     "abc": {
 *         "def": "hello"
 *     },
 *     "xyz": {
 *         "hij": 23
 *     }
 * }
 * </pre>
 *
 * <h3>Writing an Array</h3>
 *
 * <p>The following are examples of writing a JSON array.</p>
 *
 * <p>Simple array:</p>
 * <pre>
 * final JsonWriter writer = new JsonWriter();
 * writer.setPrettyPrint(true);
 * writer.startArray()
 *       .value("bar")
 *       .value(12)
 *       .value(true)
 *       .endArray();
 * </pre>
 *
 * <p>JSON written:</p>
 * <pre>
 * [
 *     "bar",
 *     12,
 *     true
 * ]
 * </pre>
 *
 * <p>Array with nested array and object:</p>
 * <pre>
 * final JsonWriter writer = new JsonWriter();
 * writer.setPrettyPrint(true);
 * writer.startArray()
 *       .startObject()
 *       .member("def", "hello")
 *       .endObject()
 *       .startArray()
 *       .value(23)
 *       .endArray()
 *       .endArray();
 * </pre>
 *
 * <p>JSON written:</p>
 * <pre>
 * [
 *     {
 *         "def": "hello"
 *     },
 *     [
 *         23
 *     ]
 * ]
 * </pre>
 */
@SuppressWarnings("UnusedReturnValue")
public class JsonWriter {

    private enum ContainerType {
        OBJECT,
        ARRAY,
    }

    private enum State {
        NONE,
        OBJECT_START,
        OBJECT_END,
        ARRAY_START,
        ARRAY_END,
        MEMBER,
        MEMBER_NAME,
        VALUE,
    }

    private final Writer out;
    private boolean prettyPrint;
    private int indent;
    private boolean writeNullMembers;
    private final Set<JsonEscaper.Option> escapeOptions;

    private String indentStr;
    private final Deque<ContainerType> containerStack;
    private State currentState;

    /**
     * Constructs a JSON writer that writes to the standard output (i.e. {@link System#out}).
     */
    public JsonWriter() {
        this(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
    }

    /**
     * Constructs a JSON writer that writes to the specified writer.
     *
     * @param writer Output writer. Will not be closed.
     */
    public JsonWriter(final Writer writer) {
        this.out = writer;
        this.indent = 4;
        this.escapeOptions = EnumSet.noneOf(JsonEscaper.Option.class);
        this.indentStr = " ".repeat(this.indent);
        this.containerStack = new LinkedList<>();
        this.currentState = State.NONE;
    }

    /**
     * Indicates whether the writer is configured to format the JSON for readability (i.e. indentation and newlines).
     * The default is {@code false}, which means that the JSON will be written without indentation and newlines.
     *
     * @return {@code true} if the writer is configured to format the JSON for readability.
     */
    public boolean isPrettyPrint() {
        return this.prettyPrint;
    }

    /**
     * Sets whether the writer should format the JSON for readability (i.e. indentation and newlines). The default
     * is {@code false}, which means that the JSON will be written without indentation and newlines.
     *
     * @param prettyPrint {@code true} to format the JSON for readability.
     */
    public void setPrettyPrint(final boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    /**
     * Escape characters above the ASCII range (i.e. ch &gt; 0x7F). By default, only ASCII control characters
     * and markup-significant ASCII characters are escaped. Specifying this option causes all ISO Latin-1,
     * Unicode BMP and surrogate pair characters to be escaped.
     *
     * @param enable {@code true} to escape characters outside the ASCII range using numerical entity references
     */
    public void setEscapeNonAscii(final boolean enable) {
        if (enable) {
            this.escapeOptions.add(JsonEscaper.Option.ESCAPE_NON_ASCII);
        } else {
            this.escapeOptions.remove(JsonEscaper.Option.ESCAPE_NON_ASCII);
        }
    }

    /**
     * Indicates whether characters above the ASCII range (i.e. ch &gt; 0x7F) are escaped.
     *
     * @return {@code true} if characters outside the ASCII range are being escaped.
     */
    public boolean getEscapeNonAscii() {
        return this.escapeOptions.contains(JsonEscaper.Option.ESCAPE_NON_ASCII);
    }

    /**
     * Obtains the number of spaces to write for each level of indentation. The default is 4.
     *
     * @return Number of spaces to write for each level of indentation.
     */
    public int getIndent() {
        return this.indent;
    }

    /**
     * Sets the number of spaces to write for each level of indentation. The default is 4.
     *
     * @param indent Number of spaces for each level of indentation.
     */
    public void setIndent(final int indent) {
        this.indent = indent;
        this.indentStr = " ".repeat(this.indent);
    }

    /**
     * Indicates whether object members whose values are {@code null} should be written. The default is {@code false}.
     *
     * @return Indicates whether {@code null} object members should be written.
     */
    public boolean isWriteNullMembers() {
        return this.writeNullMembers;
    }

    /**
     * Sets whether object members whose values are {@code null} should be written. The default is {@code false}.
     *
     * @param writeNullMembers {@code true} if {@code null} object members should be written.
     */
    public void setWriteNullMembers(final boolean writeNullMembers) {
        this.writeNullMembers = writeNullMembers;
    }

    /**
     * Writes the start of a JSON object.
     *
     * @return This writer.
     * @throws IOException if there was a problem writing the output.
     */
    public JsonWriter startObject() throws IOException {
        formatter(State.OBJECT_START);
        this.out.write('{');

        this.containerStack.push(ContainerType.OBJECT);
        return this;
    }

    /**
     * Writes the end of a JSON object.
     *
     * @return This writer.
     * @throws IOException if there was a problem writing the output.
     */
    public JsonWriter endObject() throws IOException {
        final ContainerType containerType = this.containerStack.pop();
        if (containerType != ContainerType.OBJECT) {
            throw new IllegalStateException("Expected to end object but was an array");
        }

        formatter(State.OBJECT_END);
        this.out.write('}');
        writeLastNewline();
        return this;
    }

    /**
     * Writes the start of a JSON array.
     *
     * @return This writer.
     * @throws IOException if there was a problem writing the output.
     */
    public JsonWriter startArray() throws IOException {
        formatter(State.ARRAY_START);
        this.out.write('[');

        this.containerStack.push(ContainerType.ARRAY);
        return this;
    }

    /**
     * Writes the end of a JSON array.
     *
     * @return This writer.
     * @throws IOException if there was a problem writing the output.
     */
    public JsonWriter endArray() throws IOException {
        final ContainerType containerType = this.containerStack.pop();
        if (containerType != ContainerType.ARRAY) {
            throw new IllegalStateException("Expected to end array but was an object");
        }

        formatter(State.ARRAY_END);
        this.out.write(']');
        writeLastNewline();
        return this;
    }

    /**
     * Writes an object member with the specified name and value.
     *
     * @param name Name of the object member
     * @param value Value for the object member. If the value is {@code null} and {@link #isWriteNullMembers()} is
     *      {@code false}, the member will not be written.
     * @return This writer.
     * @throws IOException if there was a problem writing the output.
     */
    public JsonWriter member(final String name, @Nullable final String value) throws IOException {
        if (value != null || this.writeNullMembers) {
            formatter(State.MEMBER);
            writeMemberStart(name);
            writeValue(value);
        }
        return this;
    }

    /**
     * Writes an object member with the specified name and value.
     *
     * @param name Name of the object member
     * @param value Value for the object member
     * @return This writer.
     * @throws IOException if there was a problem writing the output.
     */
    public JsonWriter member(final String name, final char value) throws IOException {
        formatter(State.MEMBER);
        writeMemberStart(name);
        writeValue(value);
        return this;
    }

    /**
     * Writes an object member with the specified name and value.
     *
     * @param name Name of the object member
     * @param value Value for the object member
     * @return This writer.
     * @throws IOException if there was a problem writing the output.
     */
    public JsonWriter member(final String name, final long value) throws IOException {
        formatter(State.MEMBER);
        writeMemberStart(name);
        writeValue(value);
        return this;
    }

    /**
     * Writes an object member with the specified name and value.
     *
     * @param name Name of the object member
     * @param value Value for the object member
     * @return This writer.
     * @throws IOException if there was a problem writing the output.
     */
    public JsonWriter member(final String name, final int value) throws IOException {
        formatter(State.MEMBER);
        writeMemberStart(name);
        writeValue(value);
        return this;
    }

    /**
     * Writes an object member with the specified name and value.
     *
     * @param name Name of the object member
     * @param value Value for the object member
     * @return This writer.
     * @throws IOException if there was a problem writing the output.
     */
    public JsonWriter member(final String name, final double value) throws IOException {
        formatter(State.MEMBER);
        writeMemberStart(name);
        writeValue(value);
        return this;
    }

    /**
     * Writes an object member with the specified name and value.
     *
     * @param name Name of the object member
     * @param value Value for the object member
     * @return This writer.
     * @throws IOException if there was a problem writing the output.
     */
    public JsonWriter member(final String name, final boolean value) throws IOException {
        formatter(State.MEMBER);
        writeMemberStart(name);
        writeValue(value);
        return this;
    }

    /**
     * Writes the start of an object member with the specified name. To provide a value for the member,
     * call one of the {@link #value} methods, or the {@link #startObject()} or {@link #startArray()} method.
     *
     * @param name Name of the object member
     * @return This writer.
     * @throws IOException if there was a problem writing the output.
     */
    public JsonWriter member(final String name) throws IOException {
        formatter(State.MEMBER_NAME);
        writeMemberStart(name);
        return this;
    }

    /**
     * Writes an object member with the specified name and whose value is the start of an object. Calling this
     * method is equivalent to calling:
     * <pre>
     * member(name);
     * startObject();
     * </pre>
     *
     * @param name Name of the object member
     * @return This writer.
     * @throws IOException if there was a problem writing the output.
     */
    public JsonWriter memberStartObject(final String name) throws IOException {
        member(name);
        startObject();
        return this;
    }

    /**
     * Writes an object member with the specified name and whose value is the start of an array. Calling this
     * method is equivalent to calling:
     * <pre>
     * member(name);
     * startArray();
     * </pre>
     *
     * @param name Name of the object member
     * @return This writer.
     * @throws IOException if there was a problem writing the output.
     */
    public JsonWriter memberStartArray(final String name) throws IOException {
        member(name);
        startArray();
        return this;
    }

    /**
     * Writes an object member or array value.
     *
     * @param value Value to write. Note that a {@code null} value will be written regardless of
     *      {@link #isWriteNullMembers()}.
     * @return This writer.
     * @throws IOException if there was a problem writing the output.
     */
    public JsonWriter value(@Nullable final String value) throws IOException {
        formatter(State.VALUE);
        writeValue(value);
        writeLastNewline();
        return this;
    }

    /**
     * Writes an object member or array value.
     *
     * @param value Value to write.
     * @return This writer.
     * @throws IOException if there was a problem writing the output.
     */
    public JsonWriter value(final char value) throws IOException {
        formatter(State.VALUE);
        writeValue(value);
        writeLastNewline();
        return this;
    }

    /**
     * Writes an object member or array value.
     *
     * @param value Value to write.
     * @return This writer.
     * @throws IOException if there was a problem writing the output.
     */
    public JsonWriter value(final long value) throws IOException {
        formatter(State.VALUE);
        writeValue(value);
        writeLastNewline();
        return this;
    }

    /**
     * Writes an object member or array value.
     *
     * @param value Value to write.
     * @return This writer.
     * @throws IOException if there was a problem writing the output.
     */
    public JsonWriter value(final int value) throws IOException {
        formatter(State.VALUE);
        writeValue(value);
        writeLastNewline();
        return this;
    }

    /**
     * Writes an object member or array value.
     *
     * @param value Value to write.
     * @return This writer.
     * @throws IOException if there was a problem writing the output.
     */
    public JsonWriter value(final double value) throws IOException {
        formatter(State.VALUE);
        writeValue(value);
        writeLastNewline();
        return this;
    }

    /**
     * Writes an object member or array value.
     *
     * @param value Value to write.
     * @return This writer.
     * @throws IOException if there was a problem writing the output.
     */
    public JsonWriter value(final boolean value) throws IOException {
        formatter(State.VALUE);
        writeValue(value);
        writeLastNewline();
        return this;
    }

    /**
     * State machine that handles pretty printing the output. The inputs to the machine are the last item
     * written and the next item to write. Based on that, the machine will determine what combination of a
     * comma, newline and indentation need to be written.
     *
     * @param nextState Next writing state
     * @throws IOException if there is a problem writing the output.
     */
    private void formatter(final State nextState) throws IOException {
        switch (this.currentState) {
            case NONE, MEMBER_NAME -> {
                switch (nextState) {
                    case OBJECT_START, ARRAY_START, VALUE -> {
                    }
                    default -> throw new IllegalStateException("Unexpected value: " + nextState);
                }
            }
            case OBJECT_START -> {
                switch (nextState) {
                    case OBJECT_END, MEMBER, MEMBER_NAME -> writeNewline();
                    default -> throw new IllegalStateException("Unexpected value: " + nextState);
                }
            }
            case OBJECT_END, ARRAY_END, VALUE -> {
                switch (nextState) {
                    case OBJECT_START, ARRAY_START, MEMBER, MEMBER_NAME, VALUE -> {
                        writeComma();
                        writeNewline();
                    }
                    case OBJECT_END, ARRAY_END -> writeNewline();
                    default -> throw new IllegalStateException("Unexpected value: " + nextState);
                }
            }
            case ARRAY_START -> {
                switch (nextState) {
                    case OBJECT_START, ARRAY_START, ARRAY_END, VALUE -> writeNewline();
                    default -> throw new IllegalStateException("Unexpected value: " + nextState);
                }
            }
            case MEMBER -> {
                switch (nextState) {
                    case OBJECT_END -> writeNewline();
                    case MEMBER, MEMBER_NAME -> {
                        writeComma();
                        writeNewline();
                    }
                    default -> throw new IllegalStateException("Unexpected value: " + nextState);
                }
            }
            default -> throw new IllegalStateException("Unexpected value: " + this.currentState);
        }

        this.currentState = nextState;
    }

    /**
     * Writes the start of an object member (i.e. the name and colon delimiter).
     *
     * @param name Name of the member
     * @throws IOException if there was a problem writing the output.
     */
    private void writeMemberStart(final String name) throws IOException {
        writeEscapedString(name);
        if (this.prettyPrint) {
            this.out.write(": ");
        } else {
            this.out.write(':');
        }
    }

    /**
     * Writes the specified value.
     *
     * @param value Value to write
     * @throws IOException if there was a problem writing the output.
     */
    private void writeValue(@Nullable final String value) throws IOException {
        if (value == null) {
            this.out.write("null");
        } else {
            writeEscapedString(value);
        }
    }

    /**
     * Writes the specified value.
     *
     * @param value Value to write
     * @throws IOException if there was a problem writing the output.
     */
    private void writeValue(final char value) throws IOException {
        writeEscapedString(Character.toString(value));
    }

    /**
     * Writes the specified value.
     *
     * @param value Value to write
     * @throws IOException if there was a problem writing the output.
     */
    private void writeValue(final long value) throws IOException {
        this.out.write(Long.toString(value, 10));
    }

    /**
     * Writes the specified value.
     *
     * @param value Value to write
     * @throws IOException if there was a problem writing the output.
     */
    private void writeValue(final int value) throws IOException {
        this.out.write(Integer.toString(value, 10));
    }

    /**
     * Writes the specified value.
     *
     * @param value Value to write
     * @throws IOException if there was a problem writing the output.
     */
    private void writeValue(final double value) throws IOException {
        this.out.write(Double.toString(value));
    }

    /**
     * Writes the specified value.
     *
     * @param value Value to write
     * @throws IOException if there was a problem writing the output.
     */
    private void writeValue(final boolean value) throws IOException {
        this.out.write(value ? "true" : "false");
    }

    /**
     * Writes the specified string escaping it and surrounding it in double quotes.
     *
     * @param str String to escape and quote
     * @throws IOException if there was a problem writing the output.
     */
    @AccessForTesting
    void writeEscapedString(final String str) throws IOException {
        this.out.write('"');
        JsonEscaper.escape(str, this.out, this.escapeOptions);
        this.out.write('"');
    }

    /**
     * Writes a comma.
     *
     * @throws IOException if there was a problem writing the output.
     */
    private void writeComma() throws IOException {
        this.out.write(',');
    }

    /**
     * Writes a newline if this is the final output and pretty printing is enabled.
     *
     * @throws IOException if there was a problem writing the output.
     */
    private void writeLastNewline() throws IOException {
        if (this.containerStack.isEmpty()) {
            writeNewline();
        }
    }

    /**
     * Writes a newline and indentation if pretty printing is enabled.
     *
     * @throws IOException if there was a problem writing the output.
     */
    private void writeNewline() throws IOException {
        if (this.prettyPrint) {
            this.out.write(System.lineSeparator());
            if (!this.containerStack.isEmpty()) {
                this.out.write(this.indentStr.repeat(this.containerStack.size()));
            }
        }
    }
}
