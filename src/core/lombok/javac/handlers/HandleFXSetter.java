package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import javafx.beans.property.*;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.core.AST;
import lombok.core.AnnotationValues;
import lombok.experimental.FXSetter;
import lombok.javac.Javac;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import org.mangosdk.spi.ProviderFor;

import java.util.Collection;

import static lombok.javac.Javac.*;
import static lombok.javac.handlers.JavacHandlerUtil.*;

@ProviderFor(JavacAnnotationHandler.class)
public class HandleFXSetter extends JavacAnnotationHandler<FXSetter> {
    @Override
    public void handle(AnnotationValues<FXSetter> annotation, JCTree.JCAnnotation ast, JavacNode annotationNode) {
        Collection<JavacNode> fields = annotationNode.upFromAnnotationToFields();
        deleteAnnotationIfNeccessary(annotationNode, Setter.class);
        deleteImportFromCompilationUnit(annotationNode, "lombok.AccessLevel");
        JavacNode node = annotationNode.up();
        AccessLevel level = annotation.getInstance().value();

        if (level == AccessLevel.NONE || node == null) return;

        List<JCTree.JCAnnotation> onMethod = unboxAndRemoveAnnotationParameter(ast, "onMethod", "@FXSetter(onMethod=", annotationNode);
        List<JCTree.JCAnnotation> onParam = unboxAndRemoveAnnotationParameter(ast, "onParam", "@FXSetter(onParam=", annotationNode);

        switch (node.getKind()) {
            case FIELD:
                createSetterForFields(level, fields, annotationNode, true, onMethod, onParam);
                break;
            case TYPE:
                if (!onMethod.isEmpty())
                    annotationNode.addError("'onMethod' is not supported for @FXSetter on a type.");
                if (!onParam.isEmpty()) annotationNode.addError("'onParam' is not supported for @FXSetter on a type.");
//                generateSetterForType(node, annotationNode, level, false);
                break;
        }
    }

    public void createSetterForFields(AccessLevel level, Collection<JavacNode> fieldNodes, JavacNode errorNode, boolean whineIfExists, List<JCTree.JCAnnotation> onMethod, List<JCTree.JCAnnotation> onParam) {
        for (JavacNode fieldNode : fieldNodes) {
            createSetterForField(level, fieldNode, errorNode, whineIfExists, onMethod, onParam);
        }
    }

    public void createSetterForField(AccessLevel level, JavacNode fieldNode, JavacNode sourceNode, boolean whineIfExists, List<JCTree.JCAnnotation> onMethod, List<JCTree.JCAnnotation> onParam) {

        if (fieldNode.getKind() != AST.Kind.FIELD) {
            fieldNode.addError("@FXSetter is only supported on a class or a field.");
            return;
        }

        JCTree.JCVariableDecl fieldDecl = (JCTree.JCVariableDecl) fieldNode.get();
        String methodName = toSetterName(fieldNode);

        if (methodName == null) {
            fieldNode.addWarning("Not generating setter for this field: It does not fit your @Accessors prefix list.");
            return;
        }

        for (String altName : toAllSetterNames(fieldNode)) {
            switch (methodExists(altName, fieldNode, false, 1)) {
                case EXISTS_BY_LOMBOK:
                    return;
                case EXISTS_BY_USER:
                    if (whineIfExists) {
                        String altNameExpl = "";
                        if (!altName.equals(methodName)) altNameExpl = String.format(" (%s)", altName);
                        fieldNode.addWarning(
                                String.format("Not generating %s(): A method with that name already exists%s", methodName, altNameExpl));
                    }
                    return;
                default:
                case NOT_EXISTS:
                    //continue scanning the other alt names.
            }
        }

        long access = toJavacModifier(level) | (fieldDecl.mods.flags & Flags.STATIC) | Flags.FINAL;

        JCTree.JCMethodDecl createdSetter = createSetter(access, fieldNode, fieldNode.getTreeMaker(), methodName, sourceNode, onMethod, onParam);
        injectMethod(fieldNode.up(), createdSetter);
    }

    public static JCTree.JCMethodDecl createSetter(long access, JavacNode field, JavacTreeMaker treeMaker, String setterName, JavacNode source, List<JCTree.JCAnnotation> onMethod, List<JCTree.JCAnnotation> onParam) {
        if (setterName == null) return null;

        JCTree.JCVariableDecl fieldDecl = (JCTree.JCVariableDecl) field.get();
//
//        JCTree.JCExpression fieldRef = createFieldAccessor(treeMaker, field, FieldAccess.ALWAYS_FIELD);
//        JCTree.JCMethodInvocation setterRef = treeMaker.Apply(List.<JCTree.JCExpression>nil(), fieldRef, List.<JCTree.JCExpression>of(treeMaker.Ident(fieldDecl.name)));
//
        ListBuffer<JCTree.JCStatement> statements = new ListBuffer<JCTree.JCStatement>();

        Name methodName = field.toName(setterName);
        List<JCTree.JCAnnotation> annsOnParam = copyAnnotations(onParam);
//
        long flags = JavacHandlerUtil.addFinalIfNeeded(Flags.PARAMETER, field.getContext());
        JCTree.JCVariableDecl param = treeMaker.VarDef(treeMaker.Modifiers(flags, annsOnParam), fieldDecl.name, extractType(treeMaker, field, fieldDecl.vartype), null);
//
//        statements.append(treeMaker.Exec(setterRef));
//
        JCTree.JCExpression methodType = treeMaker.Type(Javac.createVoidType(treeMaker, CTC_VOID));

        JCTree.JCBlock methodBody = treeMaker.Block(0, statements.toList());
        List<JCTree.JCTypeParameter> methodGenericParams = List.nil();
        List<JCTree.JCVariableDecl> parameters = List.of(param);
        List<JCTree.JCExpression> throwsClauses = List.nil();
        JCTree.JCExpression annotationMethodDefaultValue = null;

        List<JCTree.JCAnnotation> annsOnMethod = copyAnnotations(onMethod);
        if (isFieldDeprecated(field)) {
            annsOnMethod = annsOnMethod.prepend(treeMaker.Annotation(genJavaLangTypeRef(field, "Deprecated"), List.<JCTree.JCExpression>nil()));
        }

        JCTree.JCMethodDecl decl = recursiveSetGeneratedBy(treeMaker.MethodDef(treeMaker.Modifiers(access, annsOnMethod), methodName, methodType,
                methodGenericParams, parameters, throwsClauses, methodBody, annotationMethodDefaultValue), source.get(), field.getContext());
        copyJavadoc(field, decl, CopyJavadoc.SETTER);
        return decl;
    }

    public static JCTree.JCExpression extractType(JavacTreeMaker treeMaker, JavacNode node, JCTree.JCExpression type) {
        if (type instanceof JCTree.JCTypeApply) {
            JCTree.JCTypeApply typeApply = (JCTree.JCTypeApply) type;
            System.out.println(typeApply.getType().getClass() + " " + typeApply.getType());

            return treeMaker.TypeApply(extractType(treeMaker, node, (JCTree.JCExpression)typeApply.getType()), typeApply.arguments);

//            if (type instanceof JCTree.JCTypeApply) {
//                JCTree.JCTypeApply typeApply = (JCTree.JCTypeApply) type;
//                return treeMaker.TypeApply(genTypeRef(node, "javafx.collections.ObservableList"), typeApply.arguments);
//            } else {
//                return genTypeRef(node, "javafx.collections.ObservableList");
//            }
//            if (type instanceof JCTree.JCTypeApply) {
//                JCTree.JCTypeApply typeApply = (JCTree.JCTypeApply) type;
//                return treeMaker.TypeApply(genTypeRef(node, "javafx.collections.ObservableMap"), typeApply.arguments);
//            } else {
//                return genTypeRef(node, "javafx.collections.ObservableMap");
//            }
//            if (type instanceof JCTree.JCTypeApply) {
//                JCTree.JCTypeApply typeApply = (JCTree.JCTypeApply) type;
//                System.out.println(typeApply.getType());
//                System.out.println(typeApply.getTypeArguments());
//                return treeMaker.TypeApply(genTypeRef(node, "javafx.collections.ObservableSet"), typeApply.arguments);
//            } else {
//                return genTypeRef(node, "javafx.collections.ObservableSet");
//            }
        } else {
            if (typeMatches(BooleanProperty.class, node, type)) {
                return treeMaker.TypeIdent(CTC_BOOLEAN);
            } else if (typeMatches(DoubleProperty.class, node, type)) {
                return treeMaker.TypeIdent(CTC_DOUBLE);
            } else if (typeMatches(FloatProperty.class, node, type)) {
                return treeMaker.TypeIdent(CTC_FLOAT);
            } else if (typeMatches(IntegerProperty.class, node, type)) {
                return treeMaker.TypeIdent(CTC_INT);
            } else if (typeMatches(ListProperty.class, node, type)) {
                return genTypeRef(node, "javafx.collections.ObservableList");
            } else if (typeMatches(LongProperty.class, node, type)) {
                return treeMaker.TypeIdent(CTC_LONG);
            } else if (typeMatches(MapProperty.class, node, type)) {
                return genTypeRef(node, "javafx.collections.ObservableMap");
            } else if (typeMatches(ObjectProperty.class, node, type)) {
                return genJavaLangTypeRef(node, "Object");
            } else if (typeMatches(SetProperty.class, node, type)) {
                return genTypeRef(node, "javafx.collections.ObservableSet");
            } else if (typeMatches(StringProperty.class, node, type)) {
                return genJavaLangTypeRef(node, "String");
            } else {
                return type;
            }
        }
    }
}