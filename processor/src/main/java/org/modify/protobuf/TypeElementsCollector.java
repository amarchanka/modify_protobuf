package org.modify.protobuf;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

public class TypeElementsCollector {

    private final Messager messager;
    private final Elements elementUtils;
    private Types typeUtils;

    public TypeElementsCollector(ProcessingEnvironment processingEnv) {
        this.messager = processingEnv.getMessager();
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();
    }

    public Map<TypeElement, Set<TypeElement>> collectData(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Map<TypeElement, Set<TypeElement>> result = new HashMap<>();

        for (TypeElement typeElement : annotations) {
            for (Element annotatedElement : roundEnv.getElementsAnnotatedWith(typeElement)) {
                if (annotatedElement.getKind() == ElementKind.INTERFACE) {
                    messager.printMessage(Diagnostic.Kind.WARNING, "Interface detected", annotatedElement);
                    String fullName = ((TypeElement) annotatedElement).getQualifiedName().toString();
                    getElements(annotatedElement).stream()
                            .filter((TypeElement e) -> !hasInterface(e, fullName))
                            .forEach((TypeElement e) -> result.computeIfAbsent((TypeElement) e, (TypeElement id) -> new HashSet<>())
                                    .add((TypeElement) annotatedElement));
                } else {
                    messager.printMessage(Diagnostic.Kind.WARNING, "Not supported", annotatedElement);
                }
            }
        }

        return result;
    }

    public List<TypeElement> getElements(Element annotatedElement) {
        try {
            Class<?>[] classes = annotatedElement.getAnnotation(ImplementsBy.class).value();
            return Stream
                    .of(classes)
                    .map(Class::getCanonicalName)
                    .map(elementUtils::getTypeElement)
                    .collect(Collectors.toList());
        } catch (MirroredTypesException e) {
            return e.getTypeMirrors().stream()
                    .map(typeUtils::asElement)
                    .filter((Element element) -> element.getKind() == ElementKind.CLASS)
                    .map((Element element) -> (TypeElement) element)
                    .collect(Collectors.toList());
        }
    }

    private boolean hasInterface(TypeElement typeElement, String fullName) {
        for (TypeMirror i : typeElement.getInterfaces()) {
            if (i.toString().equals(fullName)) {
                return true;
            }
        }
        return false;
    }
}
