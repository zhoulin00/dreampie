package cn.dreampie.common.plugin.lesscss.compiler;

import org.codehaus.plexus.util.StringUtils;
import org.lesscss.LessCompiler;
import org.lesscss.LessException;
import org.lesscss.LessSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.plexus.build.incremental.BuildContext;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;

/**
 * Created by wangrenhui on 2014/7/11.
 */
public class LessCssCompiler extends AbstractLessCss{

    private Logger logger = LoggerFactory.getLogger(getClass());
    
    /**
     * The directory for compiled CSS stylesheets.
     *
     * @parameter expression="${lesscss.outputDirectory}" default-value="${project.build.directory}"
     * @required
     */
    protected File outputDirectory;

    /**
     * When <code>true</code> the LESS compiler will compress the CSS stylesheets.
     *
     * @parameter expression="${lesscss.compress}" default-value="false"
     */
    private boolean compress;

    /**
     * When <code>true</code> the plugin will watch for changes in LESS files and compile if it detects one.
     *
     * @parameter expression="${lesscss.watch}" default-value="false"
     */
    protected boolean watch=false;

    /**
     * When <code>true</code> the plugin will watch for changes in LESS files and compile if it detects one.
     *
     * @parameter expression="${lesscss.watchInterval}" default-value="1000"
     */
    private int watchInterval=1000;

    /**
     * The character encoding the LESS compiler will use for writing the CSS stylesheets.
     *
     * @parameter expression="${lesscss.encoding}" default-value="${project.build.sourceEncoding}"
     */
    private String encoding;

    /**
     * When <code>true</code> forces the LESS compiler to always compile the LESS sources. By default LESS sources are only compiled when modified (including imports) or the CSS stylesheet does not exists.
     *
     * @parameter expression="${lesscss.force}" default-value="false"
     */
    private boolean force;

    /**
     * The location of the LESS JavasSript file.
     *
     * @parameter
     */
    private File lessJs;

    /**
     * The location of the NodeJS executable.
     *
     * @parameter
     */
    private String nodeExecutable;

    /**
     * The format of the output file names.
     *
     * @parameter
     */
    private String outputFileFormat;

    private static final String FILE_NAME_FORMAT_PARAMETER_REGEX = "\\{fileName\\}";

    /**
     * Execute the MOJO.
     *
     * @throws LessCssException
     *             if something unexpected occurs.
     */
    public void execute() throws LessCssException {
        if (logger.isDebugEnabled()) {
            logger.debug("sourceDirectory = " + sourceDirectory);
            logger.debug("outputDirectory = " + outputDirectory);
            logger.debug("includes = " + Arrays.toString(includes));
            logger.debug("excludes = " + Arrays.toString(excludes));
            logger.debug("force = " + force);
            logger.debug("lessJs = " + lessJs);
            logger.debug("skip = " + skip);
        }

        if(!skip){
            executeInternal();
        } else {
            logger.info("Skipping plugin execution per configuration");
        }
    }

    private void executeInternal() throws LessCssException {
        long start = System.currentTimeMillis();

        String[] files = getIncludedFiles();

        if (files == null || files.length < 1) {
            logger.info("Nothing to compile - no LESS sources found");
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("included files = " + Arrays.toString(files));
            }

            Object lessCompiler = initLessCompiler();
            if (watch){
                logger.info("Watching "+sourceDirectory);
                if (force){
                    force=false;
                    logger.info("Disabled the 'force' flag in watch mode.");
                }
                Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                while (watch && !Thread.currentThread().isInterrupted()){
                    compileIfChanged(files, lessCompiler);
                    try {
                        Thread.sleep(watchInterval);
                    } catch (InterruptedException e) {
                        System.out.println("interrupted");
                    }
                }
            } else {
                compileIfChanged(files, lessCompiler);
            }

            logger.info("Complete Less compile job finished in " + (System.currentTimeMillis() - start) + " ms");
        }
    }

    private void compileIfChanged(String[] files, Object lessCompiler) throws LessCssException {
        try {
            for (String file : files) {
                File input = new File(sourceDirectory, file);

                buildContext.removeMessages(input);

                if(outputFileFormat != null){
                    file = outputFileFormat.replaceAll(FILE_NAME_FORMAT_PARAMETER_REGEX, file.replace(".less", ""));
                }

                File output = new File(outputDirectory, file.replace(".less", ".css"));

                if (!output.getParentFile().exists() && !output.getParentFile().mkdirs()) {
                    throw new LessCssException("Cannot create output directory " + output.getParentFile());
                }

                try {
                    LessSource lessSource = new LessSource(input);
                    if (force || !output.exists() || output.lastModified() < lessSource.getLastModifiedIncludingImports()) {
                        long compilationStarted = System.currentTimeMillis();
                        logger.info("Compiling LESS source: " + file + "...");
                        if (lessCompiler instanceof LessCompiler) {
                            ((LessCompiler) lessCompiler).compile(lessSource, output, force);
                        } else {
                            ((NodeJsLessCssCompiler) lessCompiler).compile(lessSource, output, force);
                        }
                        buildContext.refresh(output);
                        logger.info("Finished compilation to "+outputDirectory+" in " + (System.currentTimeMillis() - compilationStarted) + " ms");
                    }
                    else if (!watch) {
                        logger.info("Bypassing LESS source: " + file + " (not modified)");
                    }
                } catch (IOException e) {
                    buildContext.addMessage(input, 0, 0, "Error compiling LESS source", BuildContext.SEVERITY_ERROR, e);
                    throw new LessCssException("Error while compiling LESS source: " + file, e);
                } catch (LessException e) {
                    String message = e.getMessage();
                    if (StringUtils.isEmpty(message)) {
                        message = "Error compiling LESS source";
                    }
                    buildContext.addMessage(input, 0, 0, "Error compiling LESS source", BuildContext.SEVERITY_ERROR, e);
                    throw new LessCssException("Error while compiling LESS source: " + file, e);
                } catch (InterruptedException e) {
                    buildContext.addMessage(input, 0, 0, "Error compiling LESS source", BuildContext.SEVERITY_ERROR, e);
                    throw new LessCssException("Error while compiling LESS source: " + file, e);
                }
            }
        } finally {
            if (lessCompiler instanceof NodeJsLessCssCompiler) {
                ((NodeJsLessCssCompiler) lessCompiler).close();
            }
        }
    }

    private Object initLessCompiler() throws LessCssException {
        if (nodeExecutable != null) {
            NodeJsLessCssCompiler lessCompiler;
            try {
                lessCompiler = new NodeJsLessCssCompiler(nodeExecutable, compress, encoding, logger);
            } catch (IOException e) {
                throw new LessCssException(e.getMessage(), e);
            }
            if (lessJs != null) {
                throw new LessCssException(
                        "Custom LESS JavaScript is not currently supported when using nodeExecutable");
            }
            return lessCompiler;
        } else {
            LessCompiler lessCompiler = new LessCompiler();
            lessCompiler.setCompress(compress);
            lessCompiler.setEncoding(encoding);
            if (lessJs != null) {
                try {
                    lessCompiler.setLessJs(lessJs.toURI().toURL());
                } catch (MalformedURLException e) {
                    throw new LessCssException(
                            "Error while loading LESS JavaScript: " + lessJs.getAbsolutePath(), e);
                }
            }
            return lessCompiler;
        }
    }
}