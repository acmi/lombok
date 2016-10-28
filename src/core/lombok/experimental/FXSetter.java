package lombok.experimental;

import lombok.Setter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface FXSetter {
    /**
     * If you want your setter to be non-public, you can specify an alternate access level here.
     */
    lombok.AccessLevel value() default lombok.AccessLevel.PUBLIC;

    /**
     * Any annotations listed here are put on the generated method. The syntax for this feature is: {@code @Setter(onMethod=@__({@AnnotationsGoHere}))}
     */
    Setter.AnyAnnotation[] onMethod() default {};

    /**
     * Any annotations listed here are put on the generated method's parameter. The syntax for this feature is: {@code @Setter(onParam=@__({@AnnotationsGoHere}))}
     */
    Setter.AnyAnnotation[] onParam() default {};

    /**
     * Placeholder annotation to enable the placement of annotations on the generated code.
     * @deprecated Don't use this annotation, ever - Read the documentation.
     */
    @Deprecated
    @Retention(RetentionPolicy.SOURCE)
    @Target({})
    @interface AnyAnnotation {}
}
