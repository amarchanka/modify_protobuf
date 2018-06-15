package org.modify.protobuf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import com.google.auto.service.AutoService;

@SupportedAnnotationTypes("org.modify.protobuf.ImplementsBy")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(javax.annotation.processing.Processor.class)
public class ModifyProcessor extends AbstractProcessor {
    private Messager messager;
    private Filer filer;
    private Elements elementUtils;
    private TypeElementsCollector typeElementsCollector;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        filer = processingEnv.getFiler();
        elementUtils = processingEnv.getElementUtils();

        typeElementsCollector = new TypeElementsCollector(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Entry<TypeElement, Set<TypeElement>> entry : typeElementsCollector.collectData(annotations, roundEnv).entrySet()) {
            try {
                process(entry);
            } catch (Exception e) {
                error(entry.getValue().iterator().next(), e);
                return true;
            }
        }
        return true;
    }

    private void process(Entry<TypeElement, Set<TypeElement>> entry) throws IOException, URISyntaxException {
        TypeElement element = entry.getKey();
        Set<TypeElement> interfaces = entry.getValue();
        FileObject sourceLocation = getSourceLocation(element);
        String filename = toFilename(element);
        if (sourceLocation == null) {
            messager.printMessage(Diagnostic.Kind.WARNING, "Not found source " + filename, interfaces.iterator().next());
        } else {
            messager.printMessage(Diagnostic.Kind.WARNING, "Found source " + filename, interfaces.iterator().next());
            JavaType<?> javaType = Roaster.parse(sourceLocation.openInputStream());
            if (javaType instanceof JavaClassSource) {
                JavaClassSource javaClassSource = (JavaClassSource) javaType;
                addInterfacestoSource(javaClassSource, interfaces);
                try (Writer writer = sourceLocation.openWriter()) {
                    writer.append(CheckstyleUtil.format(javaClassSource));
                }

                PackageElement pkg = elementUtils.getPackageOf(element);
                FileObject resource;
                try {
                    resource = filer.getResource(StandardLocation.CLASS_PATH, pkg.getQualifiedName(),
                            element.getSimpleName() + JavaFileObject.Kind.CLASS.extension);
                } catch (Exception e) {
                    messager.printMessage(Diagnostic.Kind.WARNING, "Not found class for type" + element.getQualifiedName());
                    return;
                }
                if (resource.getLastModified() > 0) {
                    modifyClass(interfaces, resource);
                } else {
                    messager.printMessage(Diagnostic.Kind.WARNING, "[" + resource.toUri());
                }
            } else {
                messager.printMessage(Diagnostic.Kind.WARNING, "Unsupported  javaType" + javaType);
            }
        }
    }

    private void modifyClass(Set<TypeElement> interfaces, FileObject resource) throws IOException, FileNotFoundException {
        File file = new File(resource.toUri());
        messager.printMessage(Diagnostic.Kind.WARNING, "Found resource!!! [" + resource.toUri() + "]" + file);
        ClassReader reader;
        try (FileInputStream is = new FileInputStream(file)) {
            reader = new ClassReader(is);
        }
        ClassWriter writer = new ClassWriter(reader, 0);
        if (addInterfacestoSource(writer, reader, interfaces)) {
            try (FileOutputStream fw = new FileOutputStream(file)) {
                byte[] array = writer.toByteArray();
                fw.write(array, 0, array.length);
            }
        } else {
            messager.printMessage(Diagnostic.Kind.WARNING, "Not modified resource!!! [" + resource.toUri() + "]" + file);
        }
    }

    private void addInterfacestoSource(JavaClassSource javaClassSource, Set<TypeElement> interfaces) {
        for (TypeElement e : interfaces) {
            javaClassSource.addInterface(e.getQualifiedName().toString());
        }
    }

    private boolean addInterfacestoSource(ClassWriter writer, ClassReader reader, Set<TypeElement> interfaces) {
        boolean modified = false;
        for (TypeElement e : interfaces) {
            AddInterfaceAdapter addInterfaceAdapter = new AddInterfaceAdapter(writer, e.getQualifiedName().toString().replace('.', '/'));
            reader.accept(addInterfaceAdapter, 0);
            modified |= addInterfaceAdapter.isModified();
        }
        return modified;
    }

    public FileObject getSourceLocation(TypeElement element) throws IOException, URISyntaxException {
        try {
            FileObject resource = filer.getResource(StandardLocation.SOURCE_OUTPUT, "protobuf_unittest",
                    element.getSimpleName() + JavaFileObject.Kind.SOURCE.extension);

            if (resource.getLastModified() > 0) {
                return resource;
            }
        } catch (UnsupportedOperationException | FileNotFoundException | IllegalArgumentException ex) {
            error(element, ex);
        }
        return getSourceByOtherWay(element);
    }

    private FileObject getSourceByOtherWay(TypeElement element) throws URISyntaxException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        // Get a new instance of the standard file manager implementation
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(new DiagnosticCollector<>(), null, null);
        String filename = toFilename(element);
        Iterable<? extends JavaFileObject> compilationUnits1 = fileManager.getJavaFileObjects(filename);
        for (JavaFileObject jfo : compilationUnits1) {
            String uri = jfo.toUri().toString();
            messager.printMessage(Diagnostic.Kind.WARNING, "JavaFileObject " + uri);
            if (jfo.getLastModified() == 0) {
                messager.printMessage(Diagnostic.Kind.WARNING, "JavaFileObject " + uri + " not found.");
                if (uri.endsWith(filename)) {
                    uri = uri.substring(0, uri.length() - filename.length());
                    messager.printMessage(Diagnostic.Kind.WARNING, "New uri " + uri);
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
            for (File f : file.listFiles()) {
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

    private void error(Element element, Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage() + " " + sw.toString(), element);
    }
}
