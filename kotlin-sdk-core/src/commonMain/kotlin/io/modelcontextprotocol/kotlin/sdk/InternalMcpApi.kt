package io.modelcontextprotocol.kotlin.sdk

import kotlin.annotation.AnnotationTarget.ANNOTATION_CLASS
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.CONSTRUCTOR
import kotlin.annotation.AnnotationTarget.FIELD
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.LOCAL_VARIABLE
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlin.annotation.AnnotationTarget.PROPERTY_SETTER
import kotlin.annotation.AnnotationTarget.TYPEALIAS
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER

/**
 * Denotes that the annotated API is internal to the MCP SDK and its usage is restricted to components
 * within the SDK. It is not intended to be used in general application code or by external consumers.
 *
 * This annotation is used to communicate and enforce the limited visibility of certain APIs to prevent
 * accidental usage outside their intended context.
 *
 * Applying this annotation requires consumers to explicitly opt-in, acknowledging the warning level
 * associated with its use.
 *
 * This annotation can be used on classes, annotations, properties, fields, local variables, value
 * parameters, constructors, functions, property accessors, and type aliases.
 *
 * The annotation is retained in the binary and must be documented.
 */
@RequiresOptIn(
    message = "This API is internal, and its usage is restricted to internal components of the MCP SDK.",
    level = RequiresOptIn.Level.WARNING,
)
@MustBeDocumented
@Target(
    CLASS,
    ANNOTATION_CLASS,
    PROPERTY,
    FIELD,
    LOCAL_VARIABLE,
    VALUE_PARAMETER,
    CONSTRUCTOR,
    FUNCTION,
    PROPERTY_GETTER,
    PROPERTY_SETTER,
    TYPEALIAS,
)
@Retention(AnnotationRetention.BINARY)
public annotation class InternalMcpApi
