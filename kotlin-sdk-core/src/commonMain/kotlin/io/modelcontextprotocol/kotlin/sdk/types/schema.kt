package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Restricted schema definition for elicitation request properties.
 *
 * Supports primitive types (string, number, integer, boolean) and enum schemas
 * (single-select and multi-select). Only flat, top-level properties are allowed —
 * arbitrary nested objects or arrays are not supported.
 *
 * Implementations: [StringSchema], [NumberSchemaDefinition], [BooleanSchema], [EnumSchemaDefinition].
 */
@Serializable(with = PrimitiveSchemaDefinitionSerializer::class)
public sealed interface PrimitiveSchemaDefinition

/**
 * Defines a string-typed property in an elicitation schema.
 *
 * @property title Optional display title for the field.
 * @property description Optional description for the field.
 * @property minLength Minimum string length.
 * @property maxLength Maximum string length.
 * @property format Optional format constraint (e.g., email, URI, date).
 * @property type JSON Schema type discriminator, always `"string"`.
 * @property default Optional default value.
 */
@Serializable
public data class StringSchema(
    val title: String? = null,
    val description: String? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val format: StringSchemaFormat? = null,
    val default: String? = null,
) : PrimitiveSchemaDefinition {
    @EncodeDefault
    public val type: String = "string"
}

/**
 * Supported format constraints for [StringSchema].
 */
@Serializable
public enum class StringSchemaFormat {
    @SerialName("email")
    Email,

    @SerialName("uri")
    Uri,

    @SerialName("date")
    Date,

    @SerialName("date-time")
    DateTime,
}

/**
 * Defines a numeric property in an elicitation schema.
 *
 * Implementations: [IntegerSchema] (`"integer"`), [DoubleSchema] (`"number"`).
 */
@Serializable
public sealed interface NumberSchemaDefinition : PrimitiveSchemaDefinition

/**
 * Defines an integer-typed property in an elicitation schema.
 *
 * @property title Optional display title for the field.
 * @property description Optional description for the field.
 * @property minimum Minimum allowed value.
 * @property maximum Maximum allowed value.
 * @property type JSON Schema type discriminator, always `"integer"`.
 * @property default Optional default value.
 */
@Serializable
public data class IntegerSchema(
    val title: String? = null,
    val description: String? = null,
    val minimum: Int? = null,
    val maximum: Int? = null,
    val default: Int? = null,
) : NumberSchemaDefinition {
    @EncodeDefault
    val type: String = "integer"
}

/**
 * Defines a floating-point number property in an elicitation schema.
 *
 * @property title Optional display title for the field.
 * @property description Optional description for the field.
 * @property minimum Minimum allowed value.
 * @property maximum Maximum allowed value.
 * @property type JSON Schema type discriminator, always `"number"`.
 * @property default Optional default value.
 */
@Serializable
public data class DoubleSchema(
    val title: String? = null,
    val description: String? = null,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val default: Double? = null,
) : NumberSchemaDefinition {
    @EncodeDefault
    val type: String = "number"
}

/**
 * Defines a boolean-typed property in an elicitation schema.
 *
 * @property title Optional display title for the field.
 * @property description Optional description for the field.
 * @property type JSON Schema type discriminator, always `"boolean"`.
 * @property default Optional default value.
 */
@Serializable
public data class BooleanSchema(
    val title: String? = null,
    val description: String? = null,
    val default: Boolean? = null,
) : PrimitiveSchemaDefinition {
    @EncodeDefault
    val type: String = "boolean"
}

/**
 * Defines an enumeration property in an elicitation schema.
 *
 * Implementations: [SingleSelectEnumSchema], [MultiSelectEnumSchema], [LegacyTitledEnumSchema].
 */
@Serializable
public sealed interface EnumSchemaDefinition : PrimitiveSchemaDefinition

/**
 * Represents an enum option with a value and a display label.
 *
 * Used in [TitledSingleSelectEnumSchema] and [TitledMultiSelectEnumSchema].
 *
 * @property const The enum value.
 * @property title Display label for this option.
 */
@Serializable
public data class EnumOption(val const: String, val title: String)

/**
 * Defines a single-selection enumeration property.
 *
 * Implementations: [UntitledSingleSelectEnumSchema], [TitledSingleSelectEnumSchema].
 */
@Serializable
public sealed interface SingleSelectEnumSchema : EnumSchemaDefinition

/**
 * Defines a single-selection enumeration without display titles for options.
 *
 * @property title Optional display title for the field.
 * @property description Optional description for the field.
 * @property enumValues Array of enum values to choose from.
 * @property type JSON Schema type discriminator, always `"string"`.
 * @property default Optional default value.
 */
@Serializable
public data class UntitledSingleSelectEnumSchema(
    val title: String? = null,
    val description: String? = null,
    @SerialName("enum")
    val enumValues: List<String>,
    val default: String? = null,
) : SingleSelectEnumSchema {
    @EncodeDefault
    val type: String = "string"
}

/**
 * Defines a single-selection enumeration with display titles for each option.
 *
 * @property title Optional display title for the field.
 * @property description Optional description for the field.
 * @property oneOf Array of enum options with values and display labels.
 * @property type JSON Schema type discriminator, always `"string"`.
 * @property default Optional default value.
 */
@Serializable
public data class TitledSingleSelectEnumSchema(
    val title: String? = null,
    val description: String? = null,
    val oneOf: List<EnumOption>,
    val default: String? = null,
) : SingleSelectEnumSchema {
    @EncodeDefault
    val type: String = "string"
}

/**
 * Defines a single-selection enumeration with display names via the deprecated `enumNames` array.
 *
 * Use [TitledSingleSelectEnumSchema] instead. This class will be removed in a future version.
 *
 * @property title Optional display title for the field.
 * @property description Optional description for the field.
 * @property enumValues Array of enum values to choose from.
 * @property enumNames Display names for enum values. Non-standard according to JSON Schema 2020-12.
 * @property type JSON Schema type discriminator, always `"string"`.
 * @property default Optional default value.
 */
@Deprecated("Use TitledSingleSelectEnumSchema instead")
@Serializable
public data class LegacyTitledEnumSchema(
    val title: String? = null,
    val description: String? = null,
    @SerialName("enum")
    val enumValues: List<String>,
    val enumNames: List<String>? = null,
    val default: String? = null,
) : EnumSchemaDefinition {
    @EncodeDefault
    val type: String = "string"
}

/**
 * Defines a multiple-selection enumeration property.
 *
 * Implementations: [UntitledMultiSelectEnumSchema], [TitledMultiSelectEnumSchema].
 */
@Serializable
public sealed interface MultiSelectEnumSchema : EnumSchemaDefinition

/**
 * Defines a multiple-selection enumeration without display titles for options.
 *
 * @property title Optional display title for the field.
 * @property description Optional description for the field.
 * @property minItems Minimum number of items to select.
 * @property maxItems Maximum number of items to select.
 * @property items Schema for the array items.
 * @property type JSON Schema type discriminator, always `"array"`.
 * @property default Optional default value.
 */
@Serializable
public data class UntitledMultiSelectEnumSchema(
    val title: String? = null,
    val description: String? = null,
    val minItems: Int? = null,
    val maxItems: Int? = null,
    val items: Items,
    val default: List<String>? = null,
) : MultiSelectEnumSchema {
    @EncodeDefault
    val type: String = "array"

    /**
     * Schema for the array items with plain enum values.
     *
     * @property type JSON Schema type discriminator, always `"string"`.
     * @property enumValues Array of enum values to choose from.
     */
    @Serializable
    public data class Items(
        @SerialName("enum")
        val enumValues: List<String>,
    ) {
        @EncodeDefault
        val type: String = "string"
    }
}

/**
 * Defines a multiple-selection enumeration with display titles for each option.
 *
 * @property title Optional display title for the field.
 * @property description Optional description for the field.
 * @property minItems Minimum number of items to select.
 * @property maxItems Maximum number of items to select.
 * @property items Schema for array items with enum options and display labels.
 * @property type JSON Schema type discriminator, always `"array"`.
 * @property default Optional default value.
 */
@Serializable
public data class TitledMultiSelectEnumSchema(
    val title: String? = null,
    val description: String? = null,
    val minItems: Int? = null,
    val maxItems: Int? = null,
    val items: Items,
    val default: List<String>? = null,
) : MultiSelectEnumSchema {
    @EncodeDefault
    val type: String = "array"

    /**
     * Schema for array items with enum options and display labels.
     *
     * @property anyOf Array of enum options with values and display labels.
     */
    @Serializable
    public data class Items(val anyOf: List<EnumOption>)
}
