package io.iovo.node;

import io.iovo.node.conf.Configuration;
import io.iovo.node.service.API;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Application {
    private static final Logger logger = LogManager.getLogger();

    private static Iovo iovo;
    private static API api;

    public static void main(String[] args) throws Exception {

        handleOptions(args);

        iovo = new Iovo();
        api = new API(iovo);
        api.init();
        shutdownHook();
    }

    private static void handleOptions(String[] args) {
        Options options = new Options();
        Option portOption = Option.builder()
                .argName("p")
                .longOpt("port")
                .hasArg(true)
                .desc("Port")
                .build();

        options.addOption(portOption);

        try {
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);

            String portString = cmd.getOptionValue(portOption.getArgName());

            if (portString != null) {
                Configuration.setInt(Configuration.API_PORT, Integer.parseInt(portString));
            }

        } catch (Exception ex) {
            System.out.println(ex);
        }
    }

    private static void shutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                api.shutdown();
            } catch (final Exception e) {
                logger.error("Exception occurred shutting down IOVO node: " + e);
            }
        }, "Shutdown Hook"));
    }
}
