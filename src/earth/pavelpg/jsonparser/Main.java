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
        JsonNullParser nullParser = new JsonNullParser();
        System.out.println(nullParser.parse("null"));
        System.out.println(JsonObjectItemParser.INSTANCE.parse("\"jjjj\": null"));
    }
}

class IndexAndResult{
    public final int index;
    public final JsonValue result;

    public IndexAndResult(int index, JsonValue result) {
        this.index = index;
        this.result = result;
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
            new JsonStringParser(), new JsonArrayParser());
    public final static JsonValueParser INSTANCE = new JsonValueParser();
    protected Optional<IndexAndResult> parse(String input){
        return parse(input, new IndexAndResult(0, null));
    }
    protected Optional<IndexAndResult> parse(String input, IndexAndResult indexAndResult){
        return PARSERS.stream()
                .map(parser -> parser.parse(input, indexAndResult))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    /**
     * parse substring, pass result from previous result or create new
     * @param input string to parse
     * @param indexAndResult previous index and result
     * @param substring substring to parse
     * @param value if null then pass result from previous result, create new if otherwise
     * @return result and index of not yet parsed char
     */
    protected Optional<IndexAndResult> parseSubstring(String input, IndexAndResult indexAndResult, String substring, JsonValue value){
        if(input.startsWith(substring, indexAndResult.index)){
            return Optional.of(new IndexAndResult(indexAndResult.index + substring.length(), value == null? indexAndResult.result: value));
        }else{
            return Optional.empty();
        }
    }

    /**
     * parse the input string from indexAndResult.index till the end of the string while condition is true
     * @param input input string
     * @param indexAndResult result of prevous parsers
     * @param condition predicate
     * @param value functor from string to Optional JsonValue
     * @return result of parsing
     */
    protected Optional<IndexAndResult> parseWhile(String input, IndexAndResult indexAndResult, IntPredicate condition, Function<String, Optional<JsonValue>> value){
        String token = input.codePoints()
                .skip(indexAndResult.index)
                .takeWhile(condition)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint,StringBuilder::append)
                .toString();
        Optional<JsonValue> optionalValue = value.apply(token);
        return optionalValue.isPresent()?
                Optional.of(new IndexAndResult(indexAndResult.index + token.length(), optionalValue.get() == JsonValue.DUMMY?indexAndResult.result : optionalValue.get())):
                Optional.empty();
    }
    protected Function<IndexAndResult, Optional<IndexAndResult>> getSkipWs(String input){
        return item -> parseWhile(input, item, Character::isSpaceChar, str -> Optional.of(JsonValue.DUMMY));
    }
}
class JsonNull extends JsonValue{}
class JsonNullParser extends JsonValueParser{
    private static final JsonNull VALUE = new JsonNull();
    @Override
    protected Optional<IndexAndResult> parse(String input, IndexAndResult indexAndResult) {
        return parseSubstring(input, indexAndResult, "null", VALUE);
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
    protected Optional<IndexAndResult> parse(String input, IndexAndResult indexAndResult){
        return parseSubstring(input, indexAndResult, "true", TRUE).
                or(()->parseSubstring(input, indexAndResult, "false", FALSE) );
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
    protected Optional<IndexAndResult> parse(String input, IndexAndResult indexAndResult) {
        return parseWhile(input, indexAndResult, Character::isDigit, str -> str.length() > 0 ?
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
    protected Optional<IndexAndResult> parse(String input, IndexAndResult indexAndResult) {
        Function<IndexAndResult, Optional<IndexAndResult>> parseQuote = item -> parseSubstring(input, item, "\"", null),
            parse = item -> parseWhile(input, item, ch -> ch != '"', str -> Optional.of(new JsonString(str)));
        return Optional.of(indexAndResult).flatMap(parseQuote).flatMap(parse).flatMap(parseQuote);
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
    protected Optional<IndexAndResult> parse(String input, IndexAndResult indexAndResult) {
        Function<IndexAndResult, Optional<IndexAndResult>>
                skipWs = getSkipWs(input),
                parse = item -> JsonValueParser.INSTANCE.parse(input, item);
        Function<String, Function<IndexAndResult, Optional<IndexAndResult>>> parseStr = (String str) -> (IndexAndResult item) -> parseSubstring(input, item, str, null) ;
        Optional<IndexAndResult> firstBracket = parseStr.apply("[").apply(indexAndResult);
        if(firstBracket.isEmpty()) return Optional.empty();
        Optional<IndexAndResult> ws = firstBracket.flatMap(skipWs);
        Function<IndexAndResult, Optional<IndexAndResult>> parseComma = (IndexAndResult item) -> parseSubstring(input, item, ",", null);
        var items = Stream.iterate(ws.flatMap(parse),
                item -> item.isPresent() && item.get().result != null,
                item ->
                    item.flatMap(skipWs).flatMap(parseComma).flatMap(skipWs).flatMap(parse)
                ).collect(Collectors.toList());
        var last = ws;
        List<JsonValue> pureItems = Collections.emptyList();
        if(items.size() > 0) {
            last = items.get(items.size() - 1); // todo: ensure that the last item in list is really the last item from Stream.iterate
            if (last.isEmpty()) return Optional.empty();
            pureItems = items.stream().map(Optional::get).map(item -> item.result).collect(Collectors.toList());
        }
        var lastBracket = last.flatMap(skipWs).flatMap(parseStr.apply("]"));
        if(lastBracket.isEmpty()) return Optional.empty();
        return Optional.of(new IndexAndResult(lastBracket.get().index, new JsonArray(pureItems)));
    }
}
class JsonObject extends JsonValue{
    public final Map<String, JsonValue> value;
    public JsonObject(Map<String, JsonValue> value) {
        this.value = Collections.unmodifiableMap(value);
    }
}
class JsonObjectParser extends JsonValueParser{
    @Override
    protected Optional<IndexAndResult> parse(String input, IndexAndResult indexAndResult) {
        // todo: get rid of copy-pasted code
        Function<IndexAndResult, Optional<IndexAndResult>>
                skipWs = getSkipWs(input),
                parse = item -> JsonValueParser.INSTANCE.parse(input, item),
                stringLiteral = item -> JsonStringParser.INSTANCE.parse(input, item);
        Function<String, Function<IndexAndResult, Optional<IndexAndResult>>> parseStr = (String str) -> (IndexAndResult item) -> parseSubstring(input, item, str, null) ;
        Optional<IndexAndResult> firstBracket = parseStr.apply("{").apply(indexAndResult);
        if(firstBracket.isEmpty()) return Optional.empty();
        Optional<IndexAndResult> ws = firstBracket.flatMap(skipWs);
        return Optional.empty();
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
    protected Optional<IndexAndResult> parse(String input, IndexAndResult indexAndResult) {
        Function<IndexAndResult, Optional<IndexAndResult>>
                parseStr = item -> JsonStringParser.INSTANCE.parse(input, item),
                parseValue = item -> JsonValueParser.INSTANCE.parse(input, item),
                parseColon = item -> parseSubstring(input, item, ":", null),
                skipWs = getSkipWs(input);
        var key = parseStr.apply(indexAndResult);
        if(key.isEmpty()) return Optional.empty();
        var value = key.flatMap(skipWs).flatMap(parseColon).flatMap(skipWs).flatMap(parseValue);
        if(value.isEmpty()) return Optional.empty();
        JsonString keyJsonStr = (JsonString) key.get().result;
        String keyStr = keyJsonStr.value;
        return Optional.of(new IndexAndResult(value.get().index, new JsonObjectItem(keyStr, value.get().result)));
    }
}