package org.modify.protobuf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.JavaType;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.modify.protobuf.CustomClassWriter.AddInterfaceAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import com.google.auto.service.AutoService;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;


@SupportedAnnotationTypes("org.modify.protobuf.ImplementsBy")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(javax.annotation.processing.Processor.class)
public class ModifyProcessor extends AbstractProcessor {
    private Messager messager;
    private Filer filer;
    private JavacElements elementUtils;
    private Types typeUtils;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        filer = processingEnv.getFiler();
        JavacProcessingEnvironment javacProcessingEnv = (JavacProcessingEnvironment) processingEnv;
        elementUtils = javacProcessingEnv.getElementUtils();
        typeUtils = processingEnv.getTypeUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element annotatedElement : roundEnv.getElementsAnnotatedWith(ImplementsBy.class)) {
            try {
                process(annotatedElement);
            } catch (Exception e) {
                e.printStackTrace();
                error(annotatedElement, e.getMessage());
                return true;
            }
        }

        return true;
    }

    private void process(Element annotatedElement) throws IOException, URISyntaxException, ClassNotFoundException {
        ElementKind kind = annotatedElement.getKind();
        if (kind == ElementKind.INTERFACE) {
            messager.printMessage(Diagnostic.Kind.WARNING, "Interface detected", annotatedElement);
            
            for(Element e : getElements(annotatedElement)) {
                if (e.getKind() != ElementKind.CLASS) {
                    messager.printMessage(Diagnostic.Kind.WARNING, e.toString() + " not a class!", annotatedElement);
                    continue;
                }
                process(annotatedElement, (TypeElement) e); 
            }
        } else {
            messager.printMessage(Diagnostic.Kind.WARNING, "Not supported", annotatedElement);
        }
    }

    private void error(Element element, String msg, Object... args) {
        messager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args), element);
    }
    
    public List<Element> getElements(Element annotatedElement) {
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
                    .collect(Collectors.toList());
        }
    }

    private void process(Element annotatedElement, TypeElement element) throws IOException, URISyntaxException, ClassNotFoundException {
        FileObject sourceLocation = getSourceLocation(element);
        if (sourceLocation == null) {
            messager.printMessage(Diagnostic.Kind.WARNING, "Not found source " + toFilename(element), annotatedElement);
        } else {
            messager.printMessage(Diagnostic.Kind.WARNING, "Found source " + toFilename(element), annotatedElement);
            JavaType<?> javaType = Roaster.parse(sourceLocation.openInputStream());
            if (javaType instanceof JavaClassSource) {
                TypeElement targetClass = (TypeElement) annotatedElement;
                String simpleName = annotatedElement.getSimpleName().toString();
                JavaClassSource javaClassSource = (JavaClassSource ) javaType;
                String interfaceName = elementUtils.getPackageOf(targetClass).toString()+ '.' + simpleName;
                javaClassSource.addInterface(interfaceName);
                try (Writer writer = sourceLocation.openWriter()) {
                    writer.append(CheckstyleUtil.format(javaClassSource));
                }
//                PackageElement pkg = elementUtils.getPackageOf(element);
//                FileObject resource = filer.getResource(StandardLocation.CLASS_PATH, pkg.getQualifiedName(),
//                        element.getSimpleName() + JavaFileObject.Kind.CLASS.extension);
                
                Class<?> clazz = this.getClass().getClassLoader().loadClass(element.getQualifiedName().toString());
                String file = clazz.getProtectionDomain().getCodeSource().getLocation().getFile() + toFilename(clazz);
//                if (resource.getLastModified() > 0) {
//                    messager.printMessage(Diagnostic.Kind.WARNING, "Found resource!!! [" + resource.toUri() + "]" + file);
                   ClassReader reader;
                   try (FileInputStream is = new FileInputStream(file)) {
                       reader = new ClassReader(is);
                   }
                    ClassWriter writer = new ClassWriter(reader, 0);
                    AddInterfaceAdapter addInterfaceAdapter = new AddInterfaceAdapter(writer, targetClass.getQualifiedName().toString().replace('.', '/'));
                    reader.accept(addInterfaceAdapter, 0);
                    try (FileOutputStream fw = new FileOutputStream(file)) {
                        byte[] array = writer.toByteArray();
                        fw.write(array, 0, array.length);
                    }
//                } else {
//                    messager.printMessage(Diagnostic.Kind.WARNING, "[" + resource.toUri());
//                }
            } else {
                messager.printMessage(Diagnostic.Kind.WARNING, "Unsupported  javaType"+ javaType);
            }
        }
    }
    
    public FileObject getSourceLocation(TypeElement element) throws IOException, URISyntaxException {
        try {
            FileObject resource = filer.getResource(StandardLocation.SOURCE_OUTPUT, "protobuf_unittest",
                    element.getSimpleName() + JavaFileObject.Kind.SOURCE.extension);

            if (resource.getLastModified() > 0) {
                return resource;
            }
        } catch (UnsupportedOperationException | FileNotFoundException | IllegalArgumentException ex) {
            
        }
        return getSourceByOtherWay(element);
    }

    private FileObject getSourceByOtherWay(TypeElement element) throws URISyntaxException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        // Get a new instance of the standard file manager implementation
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(new DiagnosticCollector<>(), null, null);
        String filename = toFilename(element);
        Iterable<? extends JavaFileObject> compilationUnits1 = fileManager.getJavaFileObjects(filename);
        for(JavaFileObject jfo : compilationUnits1) {
            String uri = jfo.toUri().toString();
            messager.printMessage(Diagnostic.Kind.WARNING, "JavaFileObject "+ uri);
            if (jfo.getLastModified() == 0) {
                messager.printMessage(Diagnostic.Kind.WARNING, "JavaFileObject "+ uri + " not found.");
                if (uri.endsWith(filename)) {
                    uri = uri.substring(0, uri.length() - filename.length());
                    messager.printMessage(Diagnostic.Kind.WARNING, "New uri "+ uri);
                    File f = new File(new URI(uri));
                    File result = findName(f, filename);
                    if (result != null) {
                        messager.printMessage(Diagnostic.Kind.WARNING, "Found file" + result);
                        Iterable<? extends JavaFileObject> iterable = fileManager.getJavaFileObjects(result);
                        return iterable.iterator().next();
                    } else {
                        messager.printMessage(Diagnostic.Kind.WARNING, "AAAAAAAAAAAAAA not found file");
                    }
                }
                
            } else {
                return jfo;
            }
        }
        return null;
    }

    private File findName(File file, String fileName) {
        if (file.exists() && file.isDirectory()) {
            File result = new File(file, fileName);
            if (result.exists()) {
                return result;
            }
            for(File f : file.listFiles()) {
                result = findName(f, fileName);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
    
    private static String toFilename(TypeElement e) {
        return e.getQualifiedName().toString().replace('.', '/') + JavaFileObject.Kind.SOURCE.extension;
    }
    
    private static String toFilename(Class<?> clazz) {
        return clazz.getName().replace('.', '/') + ".class";
    }
}
