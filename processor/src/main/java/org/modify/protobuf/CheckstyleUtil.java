package org.modify.protobuf;

import java.util.Properties;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;

public class CheckstyleUtil {

    private static Properties properties = new Properties();
    static {
        properties.put(DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT, "160");
        properties.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, "space");
        properties.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");
        properties.put(DefaultCodeFormatterConstants.FORMATTER_BLANK_LINES_BETWEEN_IMPORT_GROUPS, "1");
        properties.put(DefaultCodeFormatterConstants.FORMATTER_BLANK_LINES_AFTER_IMPORTS, "1");
        properties.put(DefaultCodeFormatterConstants.FORMATTER_BLANK_LINES_AFTER_PACKAGE, "1");
        properties.put("org.eclipse.jdt.core.formatter.comment.line_length", "160");
        properties.put(JavaCore.COMPILER_SOURCE, "1.8");
    }
    
    private CheckstyleUtil() {
    }

    public static final String format(JavaClassSource javaClass) {
        return  Roaster.format(properties, javaClass.toString());
    }
}
