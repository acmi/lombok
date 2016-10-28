package lombok.javac.handlers;

import com.sun.tools.javac.tree.JCTree;
import lombok.core.AnnotationValues;
import lombok.experimental.FXGetter;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import org.mangosdk.spi.ProviderFor;

@ProviderFor(JavacAnnotationHandler.class)
public class HandleFXGetter extends JavacAnnotationHandler<FXGetter> {
    @Override
    public void handle(AnnotationValues<FXGetter> annotation, JCTree.JCAnnotation ast, JavacNode annotationNode) {

    }
}
