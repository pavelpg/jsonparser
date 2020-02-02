package earth.pavelpg.jsonparser;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) {
        JsonValueParser parser = JsonValueParser.INSTANCE;
        System.out.println(parser.parse("null"));
        System.out.println(parser.parse("true"));
        System.out.println(parser.parse("false"));
        System.out.println(parser.parse("5546456"));
        System.out.println(parser.parse("djdjiod"));
        System.out.println(parser.parse(""));
        System.out.println(parser.parse("\"djdjiod\""));
        System.out.println(parser.parse("\"djdjiod"));
        System.out.println(parser.parse("[ 1, 2, [ 1, 2, 3, [] ] ]"));
        System.out.println(parser.parse("{ 1, 2, [ 1, 2, 3, [] ] }"));
        System.out.println(parser.parse("{ \"key1\": 1, \"key2\": 2, \"key3\": [ 1, 2, 3, [{},{}] ] }"));
        JsonNullParser nullParser = new JsonNullParser();
        System.out.println(nullParser.parse("null"));
        System.out.println(JsonObjectItemParser.INSTANCE.parse("\"jjjj\": null"));
    }
}

class ParseObject {
    public final int index;
    public final JsonValue result;
    public final String string;

    public ParseObject(int index, JsonValue result, String string) {
        this.index = index;
        this.result = result;
        this.string = string;
    }

    public ParseObject(int index, JsonValue result, ParseObject parseObject) {
        this.index = index;
        this.result = result;
        this.string = parseObject.string;
    }

    @Override
    public String toString() {
        return "IndexAndResult{" +
                "index=" + index +
                ", result=" + result +
                '}';
    }
}
class JsonValue{
    public static final JsonValue DUMMY = new JsonValue();
}
class JsonValueParser{
    private static final List<JsonValueParser> PARSERS = List.of(new JsonBooleanParser(),new JsonNullParser(), new JsonNumberParser(),
            new JsonStringParser(), new JsonArrayParser(), new JsonObjectParser());
    public final static JsonValueParser INSTANCE = new JsonValueParser();
    protected Optional<ParseObject> parse(String input){
        return parse(new ParseObject(0, null, input));
    }
    protected Optional<ParseObject> parse(ParseObject parseObject){
        return PARSERS.stream()
                .map(parser -> parser.parse(parseObject))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    /**
     * parse substring, pass result from previous result or create new
     * @param parseObject previous index and result
     * @param substring substring to parse
     * @param value if null then pass result from previous result, create new if otherwise
     * @return result and index of not yet parsed char
     */
    protected Optional<ParseObject> parseSubstring(ParseObject parseObject, String substring, JsonValue value){
        String input = parseObject.string;
        if(input.startsWith(substring, parseObject.index)){
            return Optional.of(new ParseObject(parseObject.index + substring.length(), value == null? parseObject.result: value, input));
        }else{
            return Optional.empty();
        }
    }

    /**
     * parse the input string from indexAndResult.index till the end of the string while condition is true
     * @param parseObject result of previous parsers
     * @param condition predicate
     * @param value functor from string to Optional JsonValue (if not JsonValue.DUMMY)
     * @return result of parsing
     */
    protected Optional<ParseObject> parseWhile(ParseObject parseObject, IntPredicate condition, Function<String, Optional<JsonValue>> value){
        String input = parseObject.string;
        String token = input.codePoints()
                .skip(parseObject.index)
                .takeWhile(condition)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint,StringBuilder::append)
                .toString();
        Optional<JsonValue> optionalValue = value.apply(token);
        return optionalValue.map(
                jsonValue ->
                        new ParseObject(
                                parseObject.index + token.length(),
                                jsonValue == JsonValue.DUMMY? parseObject.result : jsonValue,
                                input
                        )
        );
    }
    protected Optional<ParseObject> skipWs(ParseObject item){
        return parseWhile(item, Character::isSpaceChar, str -> Optional.of(JsonValue.DUMMY));
    }
    protected Optional<ParseObject> parseValue(ParseObject item){
        return INSTANCE.parse(item);
    }
    protected Optional<ParseObject> parseMultipleItems(ParseObject parseObject, String firstTokenString, String lastTokenString, Function<ParseObject, Optional<? extends ParseObject>> parseValueItem, Function<List<JsonValue>, JsonValue> newMultipleValue) {
        Function<String, Function<ParseObject, Optional<ParseObject>>> parseStr = (String str) -> (ParseObject item) -> parseSubstring(item, str, null);
        Optional<ParseObject> firstToken = parseStr.apply(firstTokenString).apply(parseObject);
        if(firstToken.isEmpty()) return Optional.empty();
        Optional<ParseObject> ws = firstToken.flatMap(this::skipWs);
        Function<ParseObject, Optional<ParseObject>> parseComma = parseStr.apply(",");

        var items = Stream.iterate(ws.flatMap(parseValueItem),
                item -> item.isPresent() && item.get().result != null,
                item ->
                        item.flatMap(this::skipWs).flatMap(parseComma).flatMap(this::skipWs).flatMap(parseValueItem)
        ).collect(Collectors.toList());
        var last = ws;
        List<JsonValue> pureItems = Collections.emptyList();
        if(items.size() > 0) {
            last = items.get(items.size() - 1); // todo: ensure that the last item in list is really the last item from Stream.iterate
            if (last.isEmpty()) return Optional.empty();
            pureItems = items.stream().map(Optional::get).map(item -> item.result).collect(Collectors.toList());
        }
        var lastToken = last.flatMap(this::skipWs).flatMap(parseStr.apply(lastTokenString));
        if(lastToken.isEmpty()) return Optional.empty();
        return Optional.of(new ParseObject(lastToken.get().index, newMultipleValue.apply(pureItems), parseObject));
    }
}
class JsonNull extends JsonValue{}
class JsonNullParser extends JsonValueParser{
    private static final JsonNull VALUE = new JsonNull();
    @Override
    protected Optional<ParseObject> parse(ParseObject parseObject) {
        return parseSubstring(parseObject, "null", VALUE);
    }
}
class JsonBoolean extends JsonValue{
    public final boolean value;
    JsonBoolean(boolean v){value = v;}
    @Override
    public String toString() {
        return "JsonBoolean{" +
                "value=" + value +
                '}';
    }
}
class JsonBooleanParser extends JsonValueParser{
    private static final JsonBoolean
            TRUE = new JsonBoolean(true),
            FALSE = new JsonBoolean(false);
    @Override
    protected Optional<ParseObject> parse(ParseObject parseObject){
        return parseSubstring(parseObject, "true", TRUE).
                or(()->parseSubstring(parseObject, "false", FALSE) );
    }
}
class JsonNumber extends JsonValue{
    public final int value;
    JsonNumber(int v){value = v;}
    @Override
    public String toString() {
        return "JsonNumber{" +
                "value=" + value +
                '}';
    }
}
class JsonNumberParser extends JsonValueParser{
    @Override
    protected Optional<ParseObject> parse(ParseObject parseObject) {
        return parseWhile(parseObject, Character::isDigit, str -> str.length() > 0 ?
            Optional.of(new JsonNumber(Integer.parseInt(str)))
                : Optional.empty()
        );
    }
}
class JsonString extends JsonValue{
    public final String value;
    public JsonString(String value) {
        this.value = value;
    }
    @Override
    public String toString() {
        return "JsonString{" +
                "value='" + value + '\'' +
                '}';
    }
}
class JsonStringParser extends JsonValueParser{
    public static final JsonStringParser INSTANCE = new JsonStringParser();
    @Override
    protected Optional<ParseObject> parse(ParseObject parseObject) {
        Function<ParseObject, Optional<ParseObject>> parseQuote = item -> parseSubstring(item, "\"", null),
            parse = item -> parseWhile(item, ch -> ch != '"', str -> Optional.of(new JsonString(str)));
        return Optional.of(parseObject).flatMap(parseQuote).flatMap(parse).flatMap(parseQuote);
    }
}
class JsonArray extends JsonValue{
    public final List<JsonValue> value;
    public JsonArray(List<JsonValue> value) {
        this.value = Collections.unmodifiableList(value);
    }
    @Override
    public String toString() {
        return "JsonArray{" +
                "value=" + value +
                '}';
    }
}
class JsonArrayParser extends JsonValueParser{
    @Override
    protected Optional<ParseObject> parse(ParseObject parseObject) {
        final String firstTokenString = "[", lastTokenString = "]";
        final Function<ParseObject, Optional<? extends ParseObject>> parseValueItem = this::parseValue;

        return parseMultipleItems(parseObject, firstTokenString, lastTokenString, parseValueItem, JsonArray::new);
    }


}
class JsonObject extends JsonValue{
    public final List<JsonValue> value;
    public JsonObject(List<JsonValue> value) { //todo: must be JsonObjectItem
        this.value = Collections.unmodifiableList(value);
    }
    @Override
    public String toString() {
        return "JsonObject{" +
                "value=" + value +
                '}';
    }
}
class JsonObjectParser extends JsonValueParser{
    @Override
    protected Optional<ParseObject> parse(ParseObject parseObject) {
        return parseMultipleItems(parseObject, "{", "}", JsonObjectItemParser.INSTANCE::parse,
                JsonObject::new);
    }
}

/**
 * use as part of JsonObject/JsonObjectParser
 */
class JsonObjectItem extends JsonValue{
    public final String key;
    public final JsonValue value;
    JsonObjectItem(String k, JsonValue v){
        key = k;
        value = v;
    }

    @Override
    public String toString() {
        return "JsonObjectItem{" +
                "key='" + key + '\'' +
                ", value=" + value +
                '}';
    }
}
class JsonObjectItemParser extends JsonValueParser{
    public final static JsonObjectItemParser INSTANCE = new JsonObjectItemParser();
    @Override
    protected Optional<ParseObject> parse(ParseObject parseObject) {
        Function<ParseObject, Optional<ParseObject>>
                parseStr = JsonStringParser.INSTANCE::parse,
                parseColon = item -> parseSubstring(item, ":", null);
        var key = parseStr.apply(parseObject);
        if(key.isEmpty()) return Optional.empty();
        var value = key.flatMap(this::skipWs).flatMap(parseColon).flatMap(this::skipWs).flatMap(this::parseValue);
        if(value.isEmpty()) return Optional.empty();
        JsonString keyJsonStr = (JsonString) key.get().result;
        String keyStr = keyJsonStr.value;
        return Optional.of(new ParseObject(value.get().index, new JsonObjectItem(keyStr, value.get().result), parseObject));
    }
}