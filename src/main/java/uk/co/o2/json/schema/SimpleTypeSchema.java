package uk.co.o2.json.schema;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.xml.bind.DatatypeConverter;
import com.fasterxml.jackson.databind.JsonNode;

class SimpleTypeSchema implements JsonSchema {
    private SimpleType type = SimpleType.ANY;
    private Pattern pattern;
    private String format;
    private int maxLength;
    private int minLength;
    private Number minimum;
    private Number maximum;
    private boolean exclusiveMinimum;
    private boolean exclusiveMaximum;
    private List<JsonNode> enumeration;

    @Override
    public List<ErrorMessage> validate(JsonNode node) {
        List<ErrorMessage> results = new ArrayList<>();
        if (!isAcceptableType(node)) {
            results.add(new ErrorMessage("", "Invalid type: must be of type " + type.name().toLowerCase()));
        } else {
            validatePattern(node, results);
            validateFormat(node, results);
            validateRange(node, results);
            validateLength(node, results);
            validateNodeValueIsFromEnumeratedList(node, results);
        }
        return results;
    }

    @Override
    public String getDescription() {
        return type.toString().toLowerCase();
    }

    @Override
    public boolean isAcceptableType(JsonNode node) {
        return type.matches(node);
    }

    void setEnumeration(List<JsonNode> enumeration) {
        if (EnumSet.of(SimpleType.NULL, SimpleType.ANY).contains(type)) {
            throw new IllegalArgumentException("enumeration not allowed for Null or Any types");
        }
        validateEnumElementsOfSameType(enumeration);
        this.enumeration = enumeration;
    }

    void setExclusiveMinimum(boolean exclusiveMinimum) {
        validateTypeNumberOrIntegerFor("exclusiveMinimum");
        this.exclusiveMinimum = exclusiveMinimum;
    }

    void setExclusiveMaximum(boolean exclusiveMaximum) {
        validateTypeNumberOrIntegerFor("exclusiveMaximum");
        this.exclusiveMaximum = exclusiveMaximum;
    }

    void setMinimum(Number minimum) {
        validateTypeNumberOrIntegerFor("minimum");
        this.minimum = minimum;
    }

    void setMaximum(Number maximum) {
        validateTypeNumberOrIntegerFor("maximum");
        this.maximum = maximum;
    }

    private void validateEnumElementsOfSameType(List<JsonNode> values) {
        for (JsonNode value : values) {
            if (!type.matches(value)) {
                throw new IllegalArgumentException("values in enum must be of type " + type);
            }
        }
    }

    private void validateTypeNumberOrIntegerFor(String fieldName) {
        if (!EnumSet.of(SimpleType.INTEGER, SimpleType.NUMBER).contains(type)) {
            throw new IllegalArgumentException(fieldName + " can only be used for Integer or Number types");
        }
    }

    void setPattern(Pattern pattern) {
        validatePatternAndType(pattern, type);
        this.pattern = pattern;
    }

    void setType(SimpleType type) {
        validateFormatAndType(format, type);
        validatePatternAndType(pattern, type);
        this.type = type;
    }

    void setFormat(String format) {
        validateFormatAndType(format, type);
        this.format = format;
    }

    void setMaxLength(int maxLength) {
        if (type != SimpleType.STRING) {
            throw new IllegalArgumentException("maxLength can only be used for type: String");
        }
        this.maxLength = maxLength;
    }

    void setMinLength(int minLength) {
        if (type != SimpleType.STRING) {
            throw new IllegalArgumentException("minLength can only be used for type: String");
        }
        this.minLength = minLength;
    }

    private static void validatePatternAndType(Pattern pattern, SimpleType type) {
        if ((type != SimpleType.STRING) && (pattern != null)) {
            throw new IllegalArgumentException("Regex patterns are only legal for type string");
        }
    }

    private static void validateFormatAndType(String format, SimpleType type) {
        FormatValidator formatValidator = formatValidators.get(format);
        if ((formatValidator != null) && (!formatValidator.isCompatibleType(type))) {
            throw new IllegalArgumentException("Format " + format + " is not valid for type " + type.name().toLowerCase());
        }
    }

    private void validateNodeValueIsFromEnumeratedList(JsonNode node, List<ErrorMessage> results) {
        if ((enumeration!= null) && !enumeration.contains(node)) {
            results.add(new ErrorMessage("", "Value " + node.toString() + " must be one of: " + enumeration.toString()));
        }
    }

    private void validateLength(JsonNode node, List<ErrorMessage> results) {
        if (minLength > 0) {
            String value = type.getValue(node).toString();
            if (value.length() < minLength) {
                results.add(new ErrorMessage("", "Value '" + node.textValue() + "' must be greater or equal to " + minLength + " characters"));
            }
        }
        if (maxLength > 0) {
            String value = type.getValue(node).toString();
            if (value.length() > maxLength) {
                results.add(new ErrorMessage("", String.format("Value '%s' must be less or equal to %d characters", node.textValue(), maxLength)));
            }
        }
    }

    private void validateRange(JsonNode node, List<ErrorMessage> results) {
        if (this.minimum != null) {
            String nodeValueAsString = type.getValue(node).toString();
            BigDecimal value = new BigDecimal(nodeValueAsString);
            BigDecimal minimum = new BigDecimal(this.minimum.toString());
            if (exclusiveMinimum && (value.compareTo(minimum) < 1)) {
                results.add(new ErrorMessage("", "Value '" + nodeValueAsString + "' must be greater than " + minimum + " when exclusiveMinimum is true"));
            } else if (value.compareTo(minimum) < 0) {
                results.add(new ErrorMessage("", "Value '" + nodeValueAsString + "' must be greater or equal to " + minimum));
            }
        }

        if (this.maximum != null) {
            String nodeValueAsString = type.getValue(node).toString();
            BigDecimal value = new BigDecimal(nodeValueAsString);
            BigDecimal maximum = new BigDecimal(this.maximum.toString());
            if (exclusiveMaximum && value.compareTo(maximum) >= 0) {
                results.add(new ErrorMessage("", "Value '" + nodeValueAsString + "' must be less than " + maximum + " when exclusiveMaximum is true"));
            } else if (value.compareTo(maximum) > 0) {
                results.add(new ErrorMessage("", "Value '" + nodeValueAsString + "' must be less than or equal to " + maximum));
            }
        }
    }

    private void validateFormat(JsonNode node, List<ErrorMessage> results) {
        if (format != null) {
            FormatValidator formatValidator = formatValidators.get(format);
            if (formatValidator!= null && !formatValidator.isValid(node)) {
                results.add(new ErrorMessage("", "Value '" + node.textValue() + "' is not a valid " + format));
            }
        }
    }

    private void validatePattern(JsonNode node, List<ErrorMessage> results) {
        if (pattern != null) {
            String value = type.getValue(node).toString();
            if (!pattern.matcher(value).matches()) {
                results.add(new ErrorMessage("", "String value '" + value + "' does not match regex '" + pattern.pattern() + "'"));
            }
        }
    }

    SimpleType getType() {
        return type;
    }

    Pattern getPattern() {
        return pattern;
    }

    String getFormat() {
        return format;
    }

    int getMaxLength() {
        return maxLength;
    }

    int getMinLength() {
        return minLength;
    }

    Number getMinimum() {
        return minimum;
    }

    Number getMaximum() {
        return maximum;
    }

    boolean isExclusiveMinimum() {
        return exclusiveMinimum;
    }

    boolean isExclusiveMaximum() {
        return exclusiveMaximum;
    }

    List<JsonNode> getEnumeration() {
        return enumeration;
    }

    private static interface FormatValidator {

        boolean isValid(JsonNode node);
        boolean isCompatibleType(SimpleType type);

    }

    private static Map<String, FormatValidator> formatValidators = Collections.unmodifiableMap(new HashMap<String, FormatValidator>() {{
        put("date-time", new FormatValidator() {
            @Override
            public boolean isValid(JsonNode node) {
                String value = SimpleType.STRING.getValue(node).toString();
                try {
                    DatatypeConverter.parseDateTime(value);
                    return true;
                } catch (IllegalArgumentException e) {
                    return false;
                }
            }

            @Override
            public boolean isCompatibleType(SimpleType type) {
                return type == SimpleType.STRING;
            }
        });
        put("date", new FormatValidator() {
            @Override
            public boolean isValid(JsonNode node) {
                String value = SimpleType.STRING.getValue(node).toString();
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                format.setLenient(false);
                ParsePosition position = new ParsePosition(0);

                Date result = format.parse(value, position);

                String[] parts = value.substring(0, position.getIndex()).split("-");
                boolean partLengthsOk = parts.length == 3 && parts[0].length() == 4 &&
                    parts[1].length() == 2 &&
                    parts[2].length() == 2;

                boolean valueIsTooLongToBeADate = position.getIndex() < value.length();

                return (result != null) && partLengthsOk && (!valueIsTooLongToBeADate);
            }

            @Override
            public boolean isCompatibleType(SimpleType type) {
                return type == SimpleType.STRING;
            }
        });
        put("time", new FormatValidator() {
            @Override
            public boolean isValid(JsonNode node) {
                String value = SimpleType.STRING.getValue(node).toString();
                SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
                format.setLenient(false);
                ParsePosition position = new ParsePosition(0);

                Date result = format.parse(value, position);

                return (result != null) && (position.getIndex() == value.length());
            }

            @Override
            public boolean isCompatibleType(SimpleType type) {
                return type == SimpleType.STRING;
            }
        });
        put("utc-millisec", new FormatValidator() {
            @Override
            public boolean isValid(JsonNode node) {
                return true;
            }

            @Override
            public boolean isCompatibleType(SimpleType type) {
                return EnumSet.of(SimpleType.INTEGER, SimpleType.NUMBER).contains(type);
            }

        });
        put("regex", new FormatValidator() {
            @Override
            public boolean isValid(JsonNode node) {
                String value = SimpleType.STRING.getValue(node).toString();
                try {
                    //noinspection ResultOfMethodCallIgnored
                    Pattern.compile(value);
                    return true;
                } catch (PatternSyntaxException e) {
                    return false;
                }
            }

            @Override
            public boolean isCompatibleType(SimpleType type) {
                return type == SimpleType.STRING;
            }
        });
        put("uri", new FormatValidator() {
            @Override
            public boolean isValid(JsonNode node) {
                String value = SimpleType.STRING.getValue(node).toString();
                try {
                    new URI(value);
                    return true;
                } catch (URISyntaxException e) {
                    return false;
                }
            }

            @Override
            public boolean isCompatibleType(SimpleType type) {
                return type == SimpleType.STRING;
            }
        });
    }});
}