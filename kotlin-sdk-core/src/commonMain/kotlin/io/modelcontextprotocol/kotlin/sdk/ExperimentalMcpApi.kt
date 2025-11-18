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
 * Annotation marking an API as experimental and subject to changes or removal in the future.
 *
 * This annotation is used to signal that a particular element, such as a class, function, or property,
 * is part of an experimental API. Such APIs may not be stable, and their usage requires opting in
 * explicitly.
 *
 * Users of the annotated API must explicitly accept the opt-in requirement to ensure they are aware
 * of the potential instability or unfinished nature of the API.
 *
 * Targets that can be annotated include:
 * - Classes
 * - Annotation classes
 * - Properties
 * - Fields
 * - Local variables
 * - Value parameters
 * - Constructors
 * - Functions
 * - Property getters
 * - Property setters
 * - Type aliases
 */
@RequiresOptIn(
    message = "This API is experimental. It may be changed in the future without notice.",
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
public annotation class ExperimentalMcpApi
