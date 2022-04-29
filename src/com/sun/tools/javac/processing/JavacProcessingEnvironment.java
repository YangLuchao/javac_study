/*
 * Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.tools.javac.processing;

import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;

import java.net.URL;
import java.io.Closeable;
import java.io.File;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.util.*;
import javax.tools.JavaFileManager;
import javax.tools.StandardJavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.DiagnosticListener;

import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.file.FSInfo;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.jvm.*;
import com.sun.tools.javac.jvm.ClassReader.BadClassFile;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.main.JavaCompiler.CompileState;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.parser.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.Abort;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.ClientCodeException;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Convert;
import com.sun.tools.javac.util.FatalError;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.JavacMessages;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Options;

import static javax.tools.StandardLocation.*;
import static com.sun.tools.javac.util.JCDiagnostic.DiagnosticFlag.*;
import static com.sun.tools.javac.main.OptionName.*;
import static com.sun.tools.javac.code.Lint.LintCategory.PROCESSING;

/**
 * Objects of this class hold and manage the state needed to support
 * annotation processing.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
/*
注解配置：
-XprintProcessorInfo命令：输出有关请求处理程序处理哪些注解的信息。
-XprintRounds命令：输出有关注解处理循环的信息。
-processor命令：使用类的全限定名指定具体的注解处理器类，如chapter8.GetSet Processor，因为类要通过loadClass()方法来加载，该方法要求加载的类必须是全限定名。可以指定多个处理器，多个处理器用逗号隔开。
-processpath命令：指定搜索注解处理器的路径，如果没有指定此选项，默认在类路径classpath中搜索。
-proc：命令：当命令为-proc:none时不对注解进行任何处理，仅编译Java源文件；当命令为-proc:only时仅运行注解处理器，不需要编译Java源文件。
-Xprint命令：如果配置了这个命令，则会运行Javac本身提供的一个注解处理器类PrintingProcessor，这个类会打印当前正在编译的Java类的源代码。需要注意的是，指定这个命令会导致其他注解处理器类失效。
-Akey=value：可以为正在执行的注解处理器提供一些客户端参数，不过需要在注解处理器上预先配置，可以通过注解@SupportedOptions或者覆写方法getSupportedOptions()来进行配置。
 */
// 在JavacProcessingEnvironment类的构造方法中读取配置命令的值，然后通过成员变量进行保存
public class JavacProcessingEnvironment implements ProcessingEnvironment, Closeable {
    Options options;

    private final boolean printProcessorInfo;
    private final boolean printRounds;
    private final boolean verbose;
    private final boolean lint;
    private final boolean procOnly;
    private final boolean fatalErrors;
    private final boolean werror;
    private final boolean showResolveErrors;
    private boolean foundTypeProcessors;

    // 用来创建新的Java源文件、Class文件及辅助文件
    private final JavacFiler filer;
    // 用来报告错误、警告或其他提示信息
    private final JavacMessager messager;
    // 实现了javax.lang.model.util.Elements接口，用于操作Element的工具方法。
    private final JavacElements elementUtils;
    // 实现了javax.lang.model.util.Types接口，用于操作TypeMirror的工具方法
    // TypeMirror可以将Type及相关的子类型映射为TypeMirror规定的一套接口，将Symbol及相关的子类型映射为Element规定的一套接口
    // 这样就可以在注解处理器中访问Javac内部才能使用的Symbol与Type对象了。
    private final JavacTypes typeUtils;

    /**
     * Holds relevant state history of which processors have been
     * used.
     */
    private DiscoveredProcessors discoveredProcs;

    /**
     * Map of processor-specific options.
     */
    private final Map<String, String> processorOptions;

    /**
     */
    private final Set<String> unmatchedProcessorOptions;

    /**
     * Annotations implicitly processed and claimed by javac.
     */
    private final Set<String> platformAnnotations;

    /**
     * Set of packages given on command line.
     */
    private Set<PackageSymbol> specifiedPackages = Collections.emptySet();

    /** The log to be used for error reporting.
     */
    Log log;

    /** Diagnostic factory.
     */
    JCDiagnostic.Factory diags;

    /**
     * Source level of the compile.
     */
    Source source;

    private ClassLoader processorClassLoader;

    /**
     * JavacMessages object used for localization
     */
    private JavacMessages messages;

    private Context context;

    public JavacProcessingEnvironment(Context context, Iterable<? extends Processor> processors) {
        this.context = context;
        log = Log.instance(context);
        source = Source.instance(context);
        diags = JCDiagnostic.Factory.instance(context);
        options = Options.instance(context);
        // 初始化参数
        printProcessorInfo = options.isSet(XPRINTPROCESSORINFO);
        printRounds = options.isSet(XPRINTROUNDS);
        verbose = options.isSet(VERBOSE);
        lint = Lint.instance(context).isEnabled(PROCESSING);
        procOnly = options.isSet(PROC, "only") || options.isSet(XPRINT);
        fatalErrors = options.isSet("fatalEnterError");
        showResolveErrors = options.isSet("showResolveErrors");
        werror = options.isSet(WERROR);
        platformAnnotations = initPlatformAnnotations();
        foundTypeProcessors = false;

        // Initialize services before any processors are initialized
        // in case processors use them.
        filer = new JavacFiler(context);
        messager = new JavacMessager(context, this);
        elementUtils = JavacElements.instance(context);
        typeUtils = JavacTypes.instance(context);
        // processorOptions保存-Akey=value配置命令的值
        processorOptions = initProcessorOptions(context);
        unmatchedProcessorOptions = initUnmatchedProcessorOptions();
        messages = JavacMessages.instance(context);
        // 初始化processorIterator变量
        initProcessorIterator(context, processors);
    }

    private Set<String> initPlatformAnnotations() {
        Set<String> platformAnnotations = new HashSet<String>();
        platformAnnotations.add("java.lang.Deprecated");
        platformAnnotations.add("java.lang.Override");
        platformAnnotations.add("java.lang.SuppressWarnings");
        platformAnnotations.add("java.lang.annotation.Documented");
        platformAnnotations.add("java.lang.annotation.Inherited");
        platformAnnotations.add("java.lang.annotation.Retention");
        platformAnnotations.add("java.lang.annotation.Target");
        return Collections.unmodifiableSet(platformAnnotations);
    }

    private void initProcessorIterator(Context context, Iterable<? extends Processor> processors) {
        Log log   = Log.instance(context);
        Iterator<? extends Processor> processorIterator;

        if (options.isSet(XPRINT)) {
            try {
                Processor processor = PrintingProcessor.class.newInstance();
                processorIterator = List.of(processor).iterator();
            } catch (Throwable t) {
                AssertionError assertError =
                    new AssertionError("Problem instantiating PrintingProcessor.");
                assertError.initCause(t);
                throw assertError;
            }
        } else if (processors != null) {
            processorIterator = processors.iterator();
        } else {
            // 首先获取-processor命令指定的注解处理器，
            String processorNames = options.get(PROCESSOR);
            JavaFileManager fileManager = context.get(JavaFileManager.class);
            try {
                // If processorpath is not explicitly set, use the classpath.
                processorClassLoader = fileManager.hasLocation(ANNOTATION_PROCESSOR_PATH)
                    ? fileManager.getClassLoader(ANNOTATION_PROCESSOR_PATH)
                    : fileManager.getClassLoader(CLASS_PATH);

                /*
                 * If the "-processor" option is used, search the appropriate
                 * path for the named class.  Otherwise, use a service
                 * provider mechanism to create the processor iterator.
                 */
                // 当配置了-processpath命令时，会在此路径下调用fileManager.getClassLoader()方法创建对应的类加载器，
                // 否则在classpath路径下创建对应的类加载器。
                // 如果获取到的值processorNames不为空，也就是配置了-processor命令
                if (processorNames != null) {
                    // 那么创建一个NameProcessIterator迭代器对象
                    processorIterator = new NameProcessIterator(processorNames, processorClassLoader, log);
                } else {
                    // 否则创建一个ServiceIterator迭代器对象
                    processorIterator = new ServiceIterator(processorClassLoader, log);
                }
            } catch (SecurityException e) {
                /*
                 * A security exception will occur if we can't create a classloader.
                 * Ignore the exception if, with hindsight, we didn't need it anyway
                 * (i.e. no processor was specified either explicitly, or implicitly,
                 * in service configuration file.) Otherwise, we cannot continue.
                 */
                processorIterator = handleServiceLoaderUnavailability("proc.cant.create.loader", e);
            }
        }
        // 初始化discoveredProcs变量
        discoveredProcs = new DiscoveredProcessors(processorIterator);
    }

    /**
     * Returns an empty processor iterator if no processors are on the
     * relevant path, otherwise if processors are present, logs an
     * error.  Called when a service loader is unavailable for some
     * reason, either because a service loader class cannot be found
     * or because a security policy prevents class loaders from being
     * created.
     *
     * @param key The resource key to use to log an error message
     * @param e   If non-null, pass this exception to Abort
     */
    private Iterator<Processor> handleServiceLoaderUnavailability(String key, Exception e) {
        JavaFileManager fileManager = context.get(JavaFileManager.class);

        if (fileManager instanceof JavacFileManager) {
            StandardJavaFileManager standardFileManager = (JavacFileManager) fileManager;
            Iterable<? extends File> workingPath = fileManager.hasLocation(ANNOTATION_PROCESSOR_PATH)
                ? standardFileManager.getLocation(ANNOTATION_PROCESSOR_PATH)
                : standardFileManager.getLocation(CLASS_PATH);

            if (needClassLoader(options.get(PROCESSOR), workingPath) )
                handleException(key, e);

        } else {
            handleException(key, e);
        }

        java.util.List<Processor> pl = Collections.emptyList();
        return pl.iterator();
    }

    /**
     * Handle a security exception thrown during initializing the
     * Processor iterator.
     */
    private void handleException(String key, Exception e) {
        if (e != null) {
            log.error(key, e.getLocalizedMessage());
            throw new Abort(e);
        } else {
            log.error(key);
            throw new Abort();
        }
    }

    /**
     * Use a service loader appropriate for the platform to provide an
     * iterator over annotations processors.  If
     * java.util.ServiceLoader is present use it, otherwise, use
     * sun.misc.Service, otherwise fail if a loader is needed.
     */
    // ServiceIterator迭代器类代表另外一种查找注解处理器的方式，
    // 将自定义的处理器打成一个JAR包，然后在JAR包的“META-INF/services”路径下创建一个固定的文件javax.annotation.processing.Processor，
    // 在javax.annotation.processing.Processor文件中填写自定义注解处理器的全限定名，
    // 可以有多个，每个占用一行。需要注意的是，如果通过命令-processor指定了注解处理器，
    // 这种方式配置的注解处理器将不会被Javac执行。
    private class ServiceIterator implements Iterator<Processor> {
        // The to-be-wrapped iterator.
        private Iterator<?> iterator;
        private Log log;
        private Class<?> loaderClass;
        private boolean jusl;
        private Object loader;

        ServiceIterator(ClassLoader classLoader, Log log) {
            String loadMethodName;

            this.log = log;
            try {
                try {
                    loaderClass = Class.forName("java.util.ServiceLoader");
                    loadMethodName = "load";
                    jusl = true;
                } catch (ClassNotFoundException cnfe) {
                    try {
                        loaderClass = Class.forName("sun.misc.Service");
                        loadMethodName = "providers";
                        jusl = false;
                    } catch (ClassNotFoundException cnfe2) {
                        // Fail softly if a loader is not actually needed.
                        this.iterator = handleServiceLoaderUnavailability("proc.no.service",
                                                                          null);
                        return;
                    }
                }

                // java.util.ServiceLoader.load or sun.misc.Service.providers
                Method loadMethod = loaderClass.getMethod(loadMethodName,
                                                          Class.class,
                                                          ClassLoader.class);

                Object result = loadMethod.invoke(null,
                                                  Processor.class,
                                                  classLoader);

                // For java.util.ServiceLoader, we have to call another
                // method to get the iterator.
                if (jusl) {
                    loader = result; // Store ServiceLoader to call reload later
                    Method m = loaderClass.getMethod("iterator");
                    result = m.invoke(result); // serviceLoader.iterator();
                }

                // The result should now be an iterator.
                this.iterator = (Iterator<?>) result;
            } catch (Throwable t) {
                log.error("proc.service.problem");
                throw new Abort(t);
            }
        }

        public boolean hasNext() {
            try {
                return iterator.hasNext();
            } catch (Throwable t) {
                if ("ServiceConfigurationError".
                    equals(t.getClass().getSimpleName())) {
                    log.error("proc.bad.config.file", t.getLocalizedMessage());
                }
                throw new Abort(t);
            }
        }

        public Processor next() {
            try {
                return (Processor)(iterator.next());
            } catch (Throwable t) {
                if ("ServiceConfigurationError".
                    equals(t.getClass().getSimpleName())) {
                    log.error("proc.bad.config.file", t.getLocalizedMessage());
                } else {
                    log.error("proc.processor.constructor.error", t.getLocalizedMessage());
                }
                throw new Abort(t);
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

        public void close() {
            if (jusl) {
                try {
                    // Call java.util.ServiceLoader.reload
                    Method reloadMethod = loaderClass.getMethod("reload");
                    reloadMethod.invoke(loader);
                } catch(Exception e) {
                    ; // Ignore problems during a call to reload.
                }
            }
        }
    }


    // 通过全限定名查找注解处理器
    private static class NameProcessIterator implements Iterator<Processor> {
        Processor nextProc = null;
        Iterator<String> names;
        ClassLoader processorCL;
        Log log;

        NameProcessIterator(String names, ClassLoader processorCL, Log log) {
            this.names = Arrays.asList(names.split(",")).iterator();
            this.processorCL = processorCL;
            this.log = log;
        }

        public boolean hasNext() {
            if (nextProc != null)
                return true;
            else {
                if (!names.hasNext())
                    return false;
                else {
                    String processorName = names.next();

                    Processor processor;
                    try {
                        try {
                            processor =
                                (Processor) (processorCL.loadClass(processorName).newInstance());
                        } catch (ClassNotFoundException cnfe) {
                            log.error("proc.processor.not.found", processorName);
                            return false;
                        } catch (ClassCastException cce) {
                            log.error("proc.processor.wrong.type", processorName);
                            return false;
                        } catch (Exception e ) {
                            log.error("proc.processor.cant.instantiate", processorName);
                            return false;
                        }
                    } catch(ClientCodeException e) {
                        throw e;
                    } catch(Throwable t) {
                        throw new AnnotationProcessingError(t);
                    }
                    nextProc = processor;
                    return true;
                }

            }
        }

        public Processor next() {
            if (hasNext()) {
                Processor p = nextProc;
                nextProc = null;
                return p;
            } else
                throw new NoSuchElementException();
        }

        public void remove () {
            throw new UnsupportedOperationException();
        }
    }

    public boolean atLeastOneProcessor() {
        return discoveredProcs.iterator().hasNext();
    }

    private Map<String, String> initProcessorOptions(Context context) {
        Options options = Options.instance(context);
        Set<String> keySet = options.keySet();
        Map<String, String> tempOptions = new LinkedHashMap<String, String>();

        for(String key : keySet) {
            if (key.startsWith("-A") && key.length() > 2) {
                int sepIndex = key.indexOf('=');
                String candidateKey = null;
                String candidateValue = null;

                if (sepIndex == -1)
                    candidateKey = key.substring(2);
                else if (sepIndex >= 3) {
                    candidateKey = key.substring(2, sepIndex);
                    candidateValue = (sepIndex < key.length()-1)?
                        key.substring(sepIndex+1) : null;
                }
                tempOptions.put(candidateKey, candidateValue);
            }
        }

        return Collections.unmodifiableMap(tempOptions);
    }

    private Set<String> initUnmatchedProcessorOptions() {
        Set<String> unmatchedProcessorOptions = new HashSet<String>();
        unmatchedProcessorOptions.addAll(processorOptions.keySet());
        return unmatchedProcessorOptions;
    }

    /**
     * State about how a processor has been used by the tool.  If a
     * processor has been used on a prior round, its process method is
     * called on all subsequent rounds, perhaps with an empty set of
     * annotations to process.  The {@code annotatedSupported} method
     * caches the supported annotation information from the first (and
     * only) getSupportedAnnotationTypes call to the processor.
     */
    // processorIterator迭代的Processor对象的封装，
    // 但是ProcessorState对象还能保存与注解处理器配置相关的信息
    static class ProcessorState {
        // 保存的具体的注解处理器
        public Processor processor;
        // contributed表示此注解处理器是否运行过process()方法
        // 如果运行过process()方法，则这个变量的值将被设置为true
        public boolean   contributed;
        // 保存了注解处理器能够处理的注解类型
        private ArrayList<Pattern> supportedAnnotationPatterns;
        // 保存了注解处理器能够处理的注解选项
        private ArrayList<String>  supportedOptionNames;

        ProcessorState(Processor p, Log log, Source source, ProcessingEnvironment env) {
            processor = p;
            contributed = false;

            try {
                // 处理注解处理器的初始化信息
                processor.init(env);
                // 处理注解处理器支持的Java源代码版本
                checkSourceVersionCompatibility(source, log);
                // 处理注解处理器支持处理的注解类型
                supportedAnnotationPatterns = new ArrayList<Pattern>();
                // 调用注解处理器的getSupportedAnnotationType()方法来获取支持处理的注解类型
                // 并添加到supportedAnnotationPatterns集合中
                for (String importString : processor.getSupportedAnnotationTypes()) {
                    supportedAnnotationPatterns.add(importStringToPattern(importString,
                                                                          processor,
                                                                          log));
                }
                // 处理注解处理器支持的注解选项
                supportedOptionNames = new ArrayList<String>();
                // 调用注解处理器的getSupportedOptions()方法来获取支持的注解选项
                for (String optionName : processor.getSupportedOptions() ) {
                    if (checkOptionName(optionName, log))
                        supportedOptionNames.add(optionName);
                }

            } catch (ClientCodeException e) {
                throw e;
            } catch (Throwable t) {
                throw new AnnotationProcessingError(t);
            }
        }

        /**
         * Checks whether or not a processor's source version is
         * compatible with the compilation source version.  The
         * processor's source version needs to be greater than or
         * equal to the source version of the compile.
         */
        private void checkSourceVersionCompatibility(Source source, Log log) {
            // 将调用AbstractProcessor类中的getSupportedSourceVersion()方法，
            // 这个方法会读取注解@SupportedSourceVersion来获取支持的版本信息
            SourceVersion procSourceVersion = processor.getSupportedSourceVersion();

            if (procSourceVersion.compareTo(Source.toSourceVersion(source)) < 0 )  {
                log.warning("proc.processor.incompatible.source.version",
                            procSourceVersion,
                            processor.getClass().getName(),
                            source.name);
            }
        }

        private boolean checkOptionName(String optionName, Log log) {
            boolean valid = isValidOptionName(optionName);
            if (!valid)
                log.error("proc.processor.bad.option.name",
                            optionName,
                            processor.getClass().getName());
            return valid;
        }

        // 判断传入的注解是否能被当前注解处理器处理
        public boolean annotationSupported(String annotationName) {
            for(Pattern p: supportedAnnotationPatterns) {
                if (p.matcher(annotationName).matches())
                    return true;
            }
            return false;
        }

        /**
         * Remove options that are matched by this processor.
         */
        public void removeSupportedOptions(Set<String> unmatchedProcessorOptions) {
            unmatchedProcessorOptions.removeAll(supportedOptionNames);
        }
    }

    // TODO: These two classes can probably be rewritten better...
    /**
     * This class holds information about the processors that have
     * been discoverd so far as well as the means to discover more, if
     * necessary.  A single iterator should be used per round of
     * annotation processing.  The iterator first visits already
     * discovered processors then fails over to the service provider
     * mechanism if additional queries are made.
     */
    // DiscoveredProcessors类实现了Iterable<ProcessorState>接口，
    // 使用迭代器类ProcessorStateIterator来迭代ProcessorState对象，
    // 这个迭代器也是借助processorIterator来完成注解处理器迭代的
    class DiscoveredProcessors implements Iterable<ProcessorState> {

        class ProcessorStateIterator implements Iterator<ProcessorState> {
            DiscoveredProcessors psi;
            // innerIter被初始化为DiscoveredProcessors类中procStateList列表的迭代器
            Iterator<ProcessorState> innerIter;
            // onProcInterator被初始化为false
            boolean onProcInterator;

            ProcessorStateIterator(DiscoveredProcessors psi) {
                this.psi = psi;
                this.innerIter = psi.procStateList.iterator();
                this.onProcInterator = false;
            }

            public ProcessorState next() {
                // onProcInterator初始换为false
                if (!onProcInterator) {
                    // 第一次执行迭代，innerIter.hasNext()返回false
                    // 第二个ProcessorStateIterator对象，调用innerIter.hasNext()方法会返回true'
                    // 因为procStateList列表中有可迭代的元素
                    if (innerIter.hasNext())
                        return innerIter.next();
                    else
                        // 第一次执行迭代，onProcInterator置为true
                        onProcInterator = true;
                }

                if (psi.processorIterator.hasNext()) {
                    // 通过迭代器psi.processorIterator迭代所有的Processor对象并封装为ProcessorState对象
                    ProcessorState ps = new ProcessorState(psi.processorIterator.next(),
                                                           log, source, JavacProcessingEnvironment.this);
                    // 最终，所有的Processor对象会被封装为ProcessState对象并保存到procStateList列表中
                    psi.procStateList.add(ps);
                    return ps;
                } else
                    throw new NoSuchElementException();
            }

            public boolean hasNext() {
                if (onProcInterator)
                    return  psi.processorIterator.hasNext();
                else
                    return innerIter.hasNext() || psi.processorIterator.hasNext();
            }

            public void remove () {
                throw new UnsupportedOperationException();
            }

            /**
             * Run all remaining processors on the procStateList that
             * have not already run this round with an empty set of
             * annotations.
             */
            // 运行在procStateList中剩下的还没有运行过的注解处理器
            public void runContributingProcs(RoundEnvironment re) {
                if (!onProcInterator) {
                    Set<TypeElement> emptyTypeElements = Collections.emptySet();
                    while(innerIter.hasNext()) {
                        ProcessorState ps = innerIter.next();
                        if (ps.contributed)
                            callProcessor(ps.processor, emptyTypeElements, re);
                    }
                }
            }
        }

        // processorIterator保存了NameProcessIterator或ServiceIterator对象
        Iterator<? extends Processor> processorIterator;
        // procStateList保存了当前已经被封装为ProcessorState对象的所有注解处理器
        ArrayList<ProcessorState>  procStateList;

        public ProcessorStateIterator iterator() {
            // 第二次调用DiscoveredProcessors对象的iterator()方法以获取到一个新的ProcessorStateIterator对象时
            return new ProcessorStateIterator(this);
        }

        DiscoveredProcessors(Iterator<? extends Processor> processorIterator) {
            this.processorIterator = processorIterator;
            this.procStateList = new ArrayList<ProcessorState>();
        }

        /**
         * Free jar files, etc. if using a service loader.
         */
        public void close() {
            if (processorIterator != null &&
                processorIterator instanceof ServiceIterator) {
                ((ServiceIterator) processorIterator).close();
            }
        }
    }

    // 运行注解处理器
    private void discoverAndRunProcs(Context context,
                                     Set<TypeElement> annotationsPresent,
                                     List<ClassSymbol> topLevelClasses,
                                     List<PackageSymbol> packageInfoFiles) {
        // 需要处理的元素的全限定名和元素的映射
        Map<String, TypeElement> unmatchedAnnotations =
            new HashMap<String, TypeElement>(annotationsPresent.size());

        // 建立全限定名到对应TypeElement对象的映射关系
        for(TypeElement a  : annotationsPresent) {
                unmatchedAnnotations.put(a.getQualifiedName().toString(),
                                         a);
        }

        // 让处理"*"的注解处理器也有机会运行
        if (unmatchedAnnotations.size() == 0)
            unmatchedAnnotations.put("", null);

        // 可通过迭代器获取所有的注解处理器
        DiscoveredProcessors.ProcessorStateIterator psi = discoveredProcs.iterator();

        Set<Element> rootElements = new LinkedHashSet<Element>();
        rootElements.addAll(topLevelClasses);
        rootElements.addAll(packageInfoFiles);
        rootElements = Collections.unmodifiableSet(rootElements);

        // 准备这一轮Round运行的环境
        // renv就是在调用注解处理器的process()方法时传递的第2个RoundEnvironment类型的参数
        // renv会保存上一轮Round运行后的一些状态，可以在覆写process()方法时调用相关方法获取这些信息进行逻辑处理
        RoundEnvironment renv = new JavacRoundEnvironment(false,
                                                          false,
                                                          rootElements,
                                                          JavacProcessingEnvironment.this);

        // 当有待处理的注解并且有注解处理器的情况下，查找能处理注解的注解处理器并运行
        while(unmatchedAnnotations.size() > 0 && psi.hasNext() ) {
            // 调用psi.next()方法获取ProcessorState对象
            ProcessorState ps = psi.next();
            // 匹配出可以被处理的注解名
            Set<String>  matchedNames = new HashSet<String>();
            // 匹配出可以被处理的元素
            Set<TypeElement> typeElements = new LinkedHashSet<TypeElement>();
            // 查找注解处理器能够处理的注解并存储到matchedNames集合中
            // 当unmatchedAnnotations集合中存在注解类型并且也能查找到注解处理器时，查找能处理这些注解类型的注解处理器并运行
            for (Map.Entry<String, TypeElement> entry: unmatchedAnnotations.entrySet()) {
                String unmatchedAnnotationName = entry.getKey();
                // 从unmatchedAnnotations集合中查找是否含有能被当前的注解处理器ps处理的注解类型
                if (ps.annotationSupported(unmatchedAnnotationName) ) {
                    // 添加匹配出可以被处理的注解
                    matchedNames.add(unmatchedAnnotationName);
                    TypeElement te = entry.getValue();
                    if (te != null)
                        // 添加匹配出可以被处理的元素
                        // TypeElement对象就是注解处理器覆写process()方法时接收的第一个Set<? extends TypeElement>类型的参数，表示此注解处理器处理的注解类型
                        typeElements.add(te);
                }
            }
            // 当注解处理器ps能够处理某些注解或者在之前的Round中运行过此注解处理器时
            if (matchedNames.size() > 0 || ps.contributed) {
                // 调用callProcessor()方法运行此注解处理器
                boolean processingResult = callProcessor(ps.processor, typeElements, renv);
                ps.contributed = true;
                ps.removeSupportedOptions(unmatchedProcessorOptions);

                if (printProcessorInfo || verbose) {
                    log.printNoteLines("x.print.processor.info",
                            ps.processor.getClass().getName(),
                            matchedNames.toString(),
                            processingResult);
                }
                // 注解处理器执行成功，移除处理器名称
                if (processingResult) {
                    unmatchedAnnotations.keySet().removeAll(matchedNames);
                }

            }
        }
        unmatchedAnnotations.remove("");

        if (lint && unmatchedAnnotations.size() > 0) {
            // Remove annotations processed by javac
            unmatchedAnnotations.keySet().removeAll(platformAnnotations);
            if (unmatchedAnnotations.size() > 0) {
                log = Log.instance(context);
                log.warning("proc.annotations.without.processors",
                            unmatchedAnnotations.keySet());
            }
        }

        // Run contributing processors that haven't run yet
        // 再次运行之前Round中运行过的注解处理器
        psi.runContributingProcs(renv);

        // Debugging
        if (options.isSet("displayFilerState"))
            filer.displayState();
    }

    /**
     * Computes the set of annotations on the symbol in question.
     * Leave class public for external testing purposes.
     */
    public static class ComputeAnnotationSet extends
        ElementScanner7<Set<TypeElement>, Set<TypeElement>> {
        final Elements elements;

        public ComputeAnnotationSet(Elements elements) {
            super();
            this.elements = elements;
        }

        @Override
        public Set<TypeElement> visitPackage(PackageElement e, Set<TypeElement> p) {
            // Don't scan enclosed elements of a package
            return p;
        }

        @Override
        // 在这个方法中查找所有已被使用的注解类型
        public Set<TypeElement> scan(Element e, Set<TypeElement> p) {
            // 如果找到，就将注解类型对应的Element对象存到p集合中，
            // 也就是保存到了Round类中定义的类型为Set<TypeElement>的annotationsPresent集合。
            for (AnnotationMirror annotationMirror :
                     elements.getAllAnnotationMirrors(e) ) {
                Element e2 = annotationMirror.getAnnotationType().asElement();
                // 将需要处理的注解类型放入Set中
                p.add((TypeElement) e2);
            }
            return super.scan(e, p);
        }
    }

    // 真正执行注解处理器
    private boolean callProcessor(Processor proc,
                                         Set<? extends TypeElement> tes,
                                         RoundEnvironment renv) {
        try {
            // 真正执行注解处理器
            return proc.process(tes, renv);
        } catch (BadClassFile ex) {
            log.error("proc.cant.access.1", ex.sym, ex.getDetailValue());
            return false;
        } catch (CompletionFailure ex) {
            StringWriter out = new StringWriter();
            ex.printStackTrace(new PrintWriter(out));
            log.error("proc.cant.access", ex.sym, ex.getDetailValue(), out.toString());
            return false;
        } catch (ClientCodeException e) {
            throw e;
        } catch (Throwable t) {
            throw new AnnotationProcessingError(t);
        }
    }

    /**
     * Helper object for a single round of annotation processing.
     */
    class Round {
        /** The round number. */
        final int number;
        /** The context for the round. */
        final Context context;
        /** The compiler for the round. */
        final JavaCompiler compiler;
        /** The log for the round. */
        final Log log;

        /** The ASTs to be compiled. */
        List<JCCompilationUnit> roots;
        /** The classes to be compiler that have were generated. */
        Map<String, JavaFileObject> genClassFiles;

        /** The set of annotations to be processed this round. */
        // 本轮要处理的注解集
        Set<TypeElement> annotationsPresent;
        /** The set of top level classes to be processed this round. */
        List<ClassSymbol> topLevelClasses;
        /** The set of package-info files to be processed this round. */
        List<PackageSymbol> packageInfoFiles;

        /** The number of Messager errors generated in this round. */
        int nMessagerErrors;

        /** Create a round (common code). */
        private Round(Context context, int number, int priorErrors, int priorWarnings) {
            this.context = context;
            this.number = number;

            compiler = JavaCompiler.instance(context);
            log = Log.instance(context);
            log.nerrors = priorErrors;
            log.nwarnings += priorWarnings;
            log.deferDiagnostics = true;

            // the following is for the benefit of JavacProcessingEnvironment.getContext()
            JavacProcessingEnvironment.this.context = context;

            // the following will be populated as needed
            topLevelClasses  = List.nil();
            packageInfoFiles = List.nil();
        }

        /** Create the first round. */
        // 创建一个新的Round
        // 参数classSymbols列表一般为空
        Round(Context context, List<JCCompilationUnit> roots, List<ClassSymbol> classSymbols) {
            this(context, 1, 0, 0);
            this.roots = roots;
            genClassFiles = new HashMap<String,JavaFileObject>();

            compiler.todo.clear(); // free the compiler's resources

            // The reverse() in the following line is to maintain behavioural
            // compatibility with the previous revision of the code. Strictly speaking,
            // it should not be necessary, but a javah golden file test fails without it.
            topLevelClasses =
                    // 调用getTopLevelClasses()方法就是将roots列表中保存的所有编译单元下定义的顶层类追加到topLevelClasses列表中
                getTopLevelClasses(roots).prependList(classSymbols.reverse());

            packageInfoFiles = getPackageInfoFiles(roots);

            // 调用findAnnotationsPresent()方法查找在topLevelClasses列表的顶层类中使用到的注解类型
            findAnnotationsPresent();
        }

        /** Create a new round. */
        private Round(Round prev,
                Set<JavaFileObject> newSourceFiles, Map<String,JavaFileObject> newClassFiles) {
            this(prev.nextContext(),
                    prev.number+1,
                    prev.nMessagerErrors,
                    prev.compiler.log.nwarnings);
            this.genClassFiles = prev.genClassFiles;

            List<JCCompilationUnit> parsedFiles = compiler.parseFiles(newSourceFiles);
            roots = cleanTrees(prev.roots).appendList(parsedFiles);

            // Check for errors after parsing
            if (unrecoverableError())
                return;

            enterClassFiles(genClassFiles);
            List<ClassSymbol> newClasses = enterClassFiles(newClassFiles);
            genClassFiles.putAll(newClassFiles);
            enterTrees(roots);

            if (unrecoverableError())
                return;

            topLevelClasses = join(
                    getTopLevelClasses(parsedFiles),
                    getTopLevelClassesFromClasses(newClasses));

            packageInfoFiles = join(
                    getPackageInfoFiles(parsedFiles),
                    getPackageInfoFilesFromClasses(newClasses));

            findAnnotationsPresent();
        }

        /** Create the next round to be used. */
        Round next(Set<JavaFileObject> newSourceFiles, Map<String, JavaFileObject> newClassFiles) {
            try {
                return new Round(this, newSourceFiles, newClassFiles);
            } finally {
                compiler.close(false);
            }
        }

        /** Create the compiler to be used for the final compilation. */
        JavaCompiler finalCompiler(boolean errorStatus) {
            try {
                JavaCompiler c = JavaCompiler.instance(nextContext());
                c.log.nwarnings += compiler.log.nwarnings;
                if (errorStatus) {
                    c.log.nerrors += compiler.log.nerrors;
                }
                return c;
            } finally {
                compiler.close(false);
            }
        }

        /** Return the number of errors found so far in this round.
         * This may include uncoverable errors, such as parse errors,
         * and transient errors, such as missing symbols. */
        int errorCount() {
            return compiler.errorCount();
        }

        /** Return the number of warnings found so far in this round. */
        int warningCount() {
            return compiler.warningCount();
        }

        /** Return whether or not an unrecoverable error has occurred. */
        boolean unrecoverableError() {
            if (messager.errorRaised())
                return true;

            for (JCDiagnostic d: log.deferredDiagnostics) {
                switch (d.getKind()) {
                    case WARNING:
                        if (werror)
                            return true;
                        break;

                    case ERROR:
                        if (fatalErrors || !d.isFlagSet(RECOVERABLE))
                            return true;
                        break;
                }
            }

            return false;
        }

        /** Find the set of annotations present in the set of top level
         *  classes and package info files to be processed this round. */
        // 调用findAnnotationsPresent()方法查找在topLevelClasses列表的顶层类中使用到的注解类型
        // 查找所有使用到的注解类型并保存到Round类的annotationsPresent中
        void findAnnotationsPresent() {
            // 通过ComputeAnnotationSet类对语法树进行扫描，找到使用到的注解类型
            ComputeAnnotationSet annotationComputer = new ComputeAnnotationSet(elementUtils);
            // Use annotation processing to compute the set of annotations present
            annotationsPresent = new LinkedHashSet<TypeElement>();
            for (ClassSymbol classSym : topLevelClasses)
                annotationComputer.scan(classSym, annotationsPresent);
            for (PackageSymbol pkgSym : packageInfoFiles)
                annotationComputer.scan(pkgSym, annotationsPresent);
        }

        /** Enter a set of generated class files. */
        private List<ClassSymbol> enterClassFiles(Map<String, JavaFileObject> classFiles) {
            ClassReader reader = ClassReader.instance(context);
            Names names = Names.instance(context);
            List<ClassSymbol> list = List.nil();

            for (Map.Entry<String,JavaFileObject> entry : classFiles.entrySet()) {
                Name name = names.fromString(entry.getKey());
                JavaFileObject file = entry.getValue();
                if (file.getKind() != JavaFileObject.Kind.CLASS)
                    throw new AssertionError(file);
                ClassSymbol cs;
                if (isPkgInfo(file, JavaFileObject.Kind.CLASS)) {
                    Name packageName = Convert.packagePart(name);
                    PackageSymbol p = reader.enterPackage(packageName);
                    if (p.package_info == null)
                        p.package_info = reader.enterClass(Convert.shortName(name), p);
                    cs = p.package_info;
                    if (cs.classfile == null)
                        cs.classfile = file;
                } else
                    cs = reader.enterClass(name, file);
                list = list.prepend(cs);
            }
            return list.reverse();
        }

        /** Enter a set of syntax trees. */
        private void enterTrees(List<JCCompilationUnit> roots) {
            compiler.enterTrees(roots);
        }

        /** Run a processing round. */
        // 运行注解处理器
        void run(boolean lastRound, boolean errorStatus) {
            printRoundInfo(lastRound);

            TaskListener taskListener = context.get(TaskListener.class);
            if (taskListener != null)
                taskListener.started(new TaskEvent(TaskEvent.Kind.ANNOTATION_PROCESSING_ROUND));

            try {
                // 第一轮注解处理器的调用时，lastRound为false
                // lastRound为true，标识最后一轮注解处理器
                if (lastRound) {
                    filer.setLastRound(true);
                    Set<Element> emptyRootElements = Collections.emptySet(); // immutable
                    RoundEnvironment renv = new JavacRoundEnvironment(true,
                            errorStatus,
                            emptyRootElements,
                            JavacProcessingEnvironment.this);
                    discoveredProcs.iterator().runContributingProcs(renv);
                } else {
                    // 第一轮时调用
                    discoverAndRunProcs(context, annotationsPresent, topLevelClasses, packageInfoFiles);
                }
            } finally {
                if (taskListener != null)
                    taskListener.finished(new TaskEvent(TaskEvent.Kind.ANNOTATION_PROCESSING_ROUND));
            }

            nMessagerErrors = messager.errorCount();
        }

        void showDiagnostics(boolean showAll) {
            Set<JCDiagnostic.Kind> kinds = EnumSet.allOf(JCDiagnostic.Kind.class);
            if (!showAll) {
                // suppress errors, which are all presumed to be transient resolve errors
                kinds.remove(JCDiagnostic.Kind.ERROR);
            }
            log.reportDeferredDiagnostics(kinds);
        }

        /** Print info about this round. */
        private void printRoundInfo(boolean lastRound) {
            if (printRounds || verbose) {
                List<ClassSymbol> tlc = lastRound ? List.<ClassSymbol>nil() : topLevelClasses;
                Set<TypeElement> ap = lastRound ? Collections.<TypeElement>emptySet() : annotationsPresent;
                log.printNoteLines("x.print.rounds",
                        number,
                        "{" + tlc.toString(", ") + "}",
                        ap,
                        lastRound);
            }
        }

        /** Get the context for the next round of processing.
         * Important values are propogated from round to round;
         * other values are implicitly reset.
         */
        private Context nextContext() {
            Context next = new Context(context);

            Options options = Options.instance(context);
            Assert.checkNonNull(options);
            next.put(Options.optionsKey, options);

            PrintWriter out = context.get(Log.outKey);
            Assert.checkNonNull(out);
            next.put(Log.outKey, out);
            Locale locale = context.get(Locale.class);
            if (locale != null)
                next.put(Locale.class, locale);
            Assert.checkNonNull(messages);
            next.put(JavacMessages.messagesKey, messages);

            final boolean shareNames = true;
            if (shareNames) {
                Names names = Names.instance(context);
                Assert.checkNonNull(names);
                next.put(Names.namesKey, names);
            }

            DiagnosticListener<?> dl = context.get(DiagnosticListener.class);
            if (dl != null)
                next.put(DiagnosticListener.class, dl);

            TaskListener tl = context.get(TaskListener.class);
            if (tl != null)
                next.put(TaskListener.class, tl);

            FSInfo fsInfo = context.get(FSInfo.class);
            if (fsInfo != null)
                next.put(FSInfo.class, fsInfo);

            JavaFileManager jfm = context.get(JavaFileManager.class);
            Assert.checkNonNull(jfm);
            next.put(JavaFileManager.class, jfm);
            if (jfm instanceof JavacFileManager) {
                ((JavacFileManager)jfm).setContext(next);
            }

            Names names = Names.instance(context);
            Assert.checkNonNull(names);
            next.put(Names.namesKey, names);

            Keywords keywords = Keywords.instance(context);
            Assert.checkNonNull(keywords);
            next.put(Keywords.keywordsKey, keywords);

            JavaCompiler oldCompiler = JavaCompiler.instance(context);
            JavaCompiler nextCompiler = JavaCompiler.instance(next);
            nextCompiler.initRound(oldCompiler);

            filer.newRound(next);
            messager.newRound(next);
            elementUtils.setContext(next);
            typeUtils.setContext(next);

            JavacTaskImpl task = context.get(JavacTaskImpl.class);
            if (task != null) {
                next.put(JavacTaskImpl.class, task);
                task.updateContext(next);
            }

            JavacTrees trees = context.get(JavacTrees.class);
            if (trees != null) {
                next.put(JavacTrees.class, trees);
                trees.updateContext(next);
            }

            context.clear();
            return next;
        }
    }


    // 执行注解处理器处理
    public JavaCompiler doProcessing(Context context,
                                     List<JCCompilationUnit> roots,
                                     List<ClassSymbol> classSymbols,
                                     Iterable<? extends PackageSymbol> pckSymbols) {

        TaskListener taskListener = context.get(TaskListener.class);
        log = Log.instance(context);

        Set<PackageSymbol> specifiedPackages = new LinkedHashSet<PackageSymbol>();
        for (PackageSymbol psym : pckSymbols)
            specifiedPackages.add(psym);
        this.specifiedPackages = Collections.unmodifiableSet(specifiedPackages);

        Round round = new Round(context, roots, classSymbols);

        // 出现错误退出标识
        boolean errorStatus;
        // 生成新文件标识
        boolean moreToDo;
        /*
        如果注解处理器运行process()方法后产生了新的Java源文件，
        Javac会重新运行一轮注解处理器，因此只要运行一轮注解处理器后有新的Java源文件产生后，
        就会接着重新运行一轮注解处理器，直到没有新的文件产生
         */
        do {
            // 运行第一轮的注解处理器
            // 调用Round对象的run()方法来执行注解处理的逻辑，
            // Round对象代表了循环调用注解处理器处理语法树的过程
            round.run(false, false);

            // 当运行完这一轮注解处理器时，如果没有发现错误并且又有新的文件
            // 生成时，需要进行下一轮注解处理器
            errorStatus = round.unrecoverableError();
            // 如果有新的文件产生，也就是当调用moreToDo()方法返回true时
            // 需要调用当前Round对象的next()方法得到一个新的Round对象，
            // 并将保存了新产生的文件的集合传递给新的Round对象
            moreToDo = moreToDo();

            round.showDiagnostics(errorStatus || showResolveErrors);

            // 调用round.next()方法创建新的Round对象
            // 每一次循环都会创建一个Round对象
            round = round.next(
                    new LinkedHashSet<JavaFileObject>(filer.getGeneratedSourceFileObjects()),
                    new LinkedHashMap<String,JavaFileObject>(filer.getGeneratedClasses()));

             // Check for errors during setup.
            if (round.unrecoverableError())
                errorStatus = true;

        } while (moreToDo && !errorStatus);

        // 运行最后一轮注解处理器
        round.run(true, errorStatus);
        round.showDiagnostics(true);

        filer.warnIfUnclosedFiles();
        warnIfUnmatchedOptions();

        /*
         * If an annotation processor raises an error in a round,
         * that round runs to completion and one last round occurs.
         * The last round may also occur because no more source or
         * class files have been generated.  Therefore, if an error
         * was raised on either of the last *two* rounds, the compile
         * should exit with a nonzero exit code.  The current value of
         * errorStatus holds whether or not an error was raised on the
         * second to last round; errorRaised() gives the error status
         * of the last round.
         * 如果注释处理器在一轮中引发错误，则该轮运行完成并发生最后一轮。
         * 最后一轮也可能发生，因为没有生成更多的源文件或类文件。
         * 因此，如果在最后两轮中的任何一轮中出现错误，则编译应该以非零退出代码退出。
         *  errorStatus 的当前值保存倒数第二轮是否引发错误；
         * errorRaised() 给出最后一轮的错误状态。
         */
        if (messager.errorRaised()
                || werror && round.warningCount() > 0 && round.errorCount() > 0)
            errorStatus = true;

        Set<JavaFileObject> newSourceFiles =
                new LinkedHashSet<JavaFileObject>(filer.getGeneratedSourceFileObjects());
        roots = cleanTrees(round.roots);

        JavaCompiler compiler = round.finalCompiler(errorStatus);

        if (newSourceFiles.size() > 0)
            roots = roots.appendList(compiler.parseFiles(newSourceFiles));

        errorStatus = errorStatus || (compiler.errorCount() > 0);

        // Free resources
        this.close();

        if (taskListener != null)
            taskListener.finished(new TaskEvent(TaskEvent.Kind.ANNOTATION_PROCESSING));

        if (errorStatus) {
            if (compiler.errorCount() == 0)
                compiler.log.nerrors++;
            return compiler;
        }

        if (procOnly && !foundTypeProcessors) {
            compiler.todo.clear();
        } else {
            if (procOnly && foundTypeProcessors)
                compiler.shouldStopPolicy = CompileState.FLOW;

            compiler.enterTrees(roots);
        }

        return compiler;
    }

    private void warnIfUnmatchedOptions() {
        if (!unmatchedProcessorOptions.isEmpty()) {
            log.warning("proc.unmatched.processor.options", unmatchedProcessorOptions.toString());
        }
    }

    /**
     * Free resources related to annotation processing.
     */
    public void close() {
        filer.close();
        if (discoveredProcs != null) // Make calling close idempotent
            discoveredProcs.close();
        discoveredProcs = null;
        if (processorClassLoader != null && processorClassLoader instanceof Closeable) {
            try {
                ((Closeable) processorClassLoader).close();
            } catch (IOException e) {
                JCDiagnostic msg = diags.fragment("fatal.err.cant.close.loader");
                throw new FatalError(msg, e);
            }
        }
    }

    private List<ClassSymbol> getTopLevelClasses(List<? extends JCCompilationUnit> units) {
        List<ClassSymbol> classes = List.nil();
        for (JCCompilationUnit unit : units) {
            for (JCTree node : unit.defs) {
                if (node.getTag() == JCTree.CLASSDEF) {
                    ClassSymbol sym = ((JCClassDecl) node).sym;
                    Assert.checkNonNull(sym);
                    classes = classes.prepend(sym);
                }
            }
        }
        return classes.reverse();
    }

    private List<ClassSymbol> getTopLevelClassesFromClasses(List<? extends ClassSymbol> syms) {
        List<ClassSymbol> classes = List.nil();
        for (ClassSymbol sym : syms) {
            if (!isPkgInfo(sym)) {
                classes = classes.prepend(sym);
            }
        }
        return classes.reverse();
    }

    private List<PackageSymbol> getPackageInfoFiles(List<? extends JCCompilationUnit> units) {
        List<PackageSymbol> packages = List.nil();
        for (JCCompilationUnit unit : units) {
            if (isPkgInfo(unit.sourcefile, JavaFileObject.Kind.SOURCE)) {
                packages = packages.prepend(unit.packge);
            }
        }
        return packages.reverse();
    }

    private List<PackageSymbol> getPackageInfoFilesFromClasses(List<? extends ClassSymbol> syms) {
        List<PackageSymbol> packages = List.nil();
        for (ClassSymbol sym : syms) {
            if (isPkgInfo(sym)) {
                packages = packages.prepend((PackageSymbol) sym.owner);
            }
        }
        return packages.reverse();
    }

    // avoid unchecked warning from use of varargs
    private static <T> List<T> join(List<T> list1, List<T> list2) {
        return list1.appendList(list2);
    }

    private boolean isPkgInfo(JavaFileObject fo, JavaFileObject.Kind kind) {
        return fo.isNameCompatible("package-info", kind);
    }

    private boolean isPkgInfo(ClassSymbol sym) {
        return isPkgInfo(sym.classfile, JavaFileObject.Kind.CLASS) && (sym.packge().package_info == sym);
    }

    /*
     * Called retroactively to determine if a class loader was required,
     * after we have failed to create one.
     */
    private boolean needClassLoader(String procNames, Iterable<? extends File> workingpath) {
        if (procNames != null)
            return true;

        String procPath;
        URL[] urls = new URL[1];
        for(File pathElement : workingpath) {
            try {
                urls[0] = pathElement.toURI().toURL();
                if (ServiceProxy.hasService(Processor.class, urls))
                    return true;
            } catch (MalformedURLException ex) {
                throw new AssertionError(ex);
            }
            catch (ServiceProxy.ServiceConfigurationError e) {
                log.error("proc.bad.config.file", e.getLocalizedMessage());
                return true;
            }
        }

        return false;
    }

    private static <T extends JCTree> List<T> cleanTrees(List<T> nodes) {
        for (T node : nodes)
            treeCleaner.scan(node);
        return nodes;
    }

    private static TreeScanner treeCleaner = new TreeScanner() {
            public void scan(JCTree node) {
                super.scan(node);
                if (node != null)
                    node.type = null;
            }
            public void visitTopLevel(JCCompilationUnit node) {
                node.packge = null;
                super.visitTopLevel(node);
            }
            public void visitClassDef(JCClassDecl node) {
                node.sym = null;
                super.visitClassDef(node);
            }
            public void visitMethodDef(JCMethodDecl node) {
                node.sym = null;
                super.visitMethodDef(node);
            }
            public void visitVarDef(JCVariableDecl node) {
                node.sym = null;
                super.visitVarDef(node);
            }
            public void visitNewClass(JCNewClass node) {
                node.constructor = null;
                super.visitNewClass(node);
            }
            public void visitAssignop(JCAssignOp node) {
                node.operator = null;
                super.visitAssignop(node);
            }
            public void visitUnary(JCUnary node) {
                node.operator = null;
                super.visitUnary(node);
            }
            public void visitBinary(JCBinary node) {
                node.operator = null;
                super.visitBinary(node);
            }
            public void visitSelect(JCFieldAccess node) {
                node.sym = null;
                super.visitSelect(node);
            }
            public void visitIdent(JCIdent node) {
                node.sym = null;
                super.visitIdent(node);
            }
        };


    private boolean moreToDo() {
        return filer.newFiles();
    }

    /**
     * {@inheritdoc}
     *
     * Command line options suitable for presenting to annotation
     * processors.  "-Afoo=bar" should be "-Afoo" => "bar".
     */
    public Map<String,String> getOptions() {
        return processorOptions;
    }

    public Messager getMessager() {
        return messager;
    }

    public Filer getFiler() {
        return filer;
    }

    public JavacElements getElementUtils() {
        return elementUtils;
    }

    public JavacTypes getTypeUtils() {
        return typeUtils;
    }

    public SourceVersion getSourceVersion() {
        return Source.toSourceVersion(source);
    }

    public Locale getLocale() {
        return messages.getCurrentLocale();
    }

    public Set<Symbol.PackageSymbol> getSpecifiedPackages() {
        return specifiedPackages;
    }

    private static final Pattern allMatches = Pattern.compile(".*");
    public static final Pattern noMatches  = Pattern.compile("(\\P{all})+");

    /**
     * Convert import-style string for supported annotations into a
     * regex matching that string.  If the string is a valid
     * import-style string, return a regex that won't match anything.
     */
    private static Pattern importStringToPattern(String s, Processor p, Log log) {
        if (isValidImportString(s)) {
            return validImportStringToPattern(s);
        } else {
            log.warning("proc.malformed.supported.string", s, p.getClass().getName());
            return noMatches; // won't match any valid identifier
        }
    }

    /**
     * Return true if the argument string is a valid import-style
     * string specifying claimed annotations; return false otherwise.
     */
    public static boolean isValidImportString(String s) {
        if (s.equals("*"))
            return true;

        boolean valid = true;
        String t = s;
        int index = t.indexOf('*');

        if (index != -1) {
            // '*' must be last character...
            if (index == t.length() -1) {
                // ... any and preceding character must be '.'
                if ( index-1 >= 0 ) {
                    valid = t.charAt(index-1) == '.';
                    // Strip off ".*$" for identifier checks
                    t = t.substring(0, t.length()-2);
                }
            } else
                return false;
        }

        // Verify string is off the form (javaId \.)+ or javaId
        if (valid) {
            String[] javaIds = t.split("\\.", t.length()+2);
            for(String javaId: javaIds)
                valid &= SourceVersion.isIdentifier(javaId);
        }
        return valid;
    }

    public static Pattern validImportStringToPattern(String s) {
        if (s.equals("*")) {
            return allMatches;
        } else {
            String s_prime = s.replace(".", "\\.");

            if (s_prime.endsWith("*")) {
                s_prime =  s_prime.substring(0, s_prime.length() - 1) + ".+";
            }

            return Pattern.compile(s_prime);
        }
    }

    /**
     * For internal use only.  This method will be
     * removed without warning.
     */
    public Context getContext() {
        return context;
    }

    public String toString() {
        return "javac ProcessingEnvironment";
    }

    public static boolean isValidOptionName(String optionName) {
        for(String s : optionName.split("\\.", -1)) {
            if (!SourceVersion.isIdentifier(s))
                return false;
        }
        return true;
    }
}
