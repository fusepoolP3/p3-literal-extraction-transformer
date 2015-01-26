package eu.fusepool.transformer.literalextraction;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.fusepool.p3.transformer.server.TransformerServer;

/**
 * Main for the Any23 transformer.<p>
 * Uses the {@link TransformerServer} to startup an environment based on an
 * embedded Jetty server.<p>
 * The <code>-h</code> option will print an help screen with the different
 * options.
 * @author westei
 *
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    
    private static final Options options;
    private static final int DEFAULT_PORT = 8080;
    
    static {
        options = new Options();
        options.addOption("h", "help", false, "display this help and exit");
        options.addOption("p","port",true, 
            String.format("the port for the literal extraction transformer (default: %s)",
                DEFAULT_PORT));
        options.addOption("t", "thread-pool", true, "The number of threads used"
                + "to process requests (default: "
                + LiteralExtractionTransformer.POOL_SIZE + ").");
 
    }

    
    public static void main(String[] args) throws Exception {
        log.info("> Literal Extraction Transformer Server");
        CommandLineParser parser = new PosixParser();
        CommandLine line = parser.parse(options, args);
        args = line.getArgs();
        if(line.hasOption('h')){
            printHelp();
            System.exit(0);
        }
        int port = -1;
        if(line.hasOption('p')){
            String portStr = line.getOptionValue('p');
            try {
                port = Integer.parseInt(portStr);
                if(port <= 0){
                    log.error("The parsed Port '{}' MUST BE an positive integer", portStr);
                    System.exit(1);
                }
            } catch (NumberFormatException e) {
                log.error(" parsed Port '{}' is not an integer", portStr);
                System.exit(1);
            }
        } else {
            port = DEFAULT_PORT;
        }
        log.info("    - port: {}",port);
        
        int threadPoolSize = -1;
        if(line.hasOption('t')){
            String value = line.getOptionValue('t');
            try {
                threadPoolSize = Integer.parseInt(value);
                if(threadPoolSize <= 0){
                    log.error("The parsed core thread pool size '{}' MUST BE an positive integer", value);
                    System.exit(1);
                }
            } catch (NumberFormatException e) {
                log.error(" parsed parsed core thread pool size '{}' is not an integer", value);
                System.exit(1);
            }
        } else {
            threadPoolSize = LiteralExtractionTransformer.POOL_SIZE;
        }

        log.info(" ... init Transformer ...");
        //TODO: allow to configure the predicates
        LiteralExtractionTransformer transformer = new LiteralExtractionTransformer();
        transformer.setThreadPoolSize(threadPoolSize);
        
        log.info(" ... init Server on port {}...", port);
        TransformerServer server = new TransformerServer(port,true);
        log.info(" ... start Server ...");
        server.start(transformer);
        //log.info(" ... shutdown ...");
        //transformer.close();
    }
    
    /**
     * 
     */
    private static void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(
            "java -Xmx{size} -jar {jar-name} [options]",
            "Literal Extracttion Transformer: \n",
            options,
            "provided by Fusepool P3");
    }

}
