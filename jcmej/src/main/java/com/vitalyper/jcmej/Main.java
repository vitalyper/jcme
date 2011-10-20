package com.vitalyper.jcmej;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSBindingFactory;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;


public class Main {
	public static final String LOG_FILE = String.format("%s%s",
			System.getenv("HOME"), "/jcmej.log");
	public static Level LOG_LEVEL = Level.INFO;
	
    static JAXRSServerFactoryBean cxfFctryBean = new JAXRSServerFactoryBean();
    static Logger logger = Logger.getLogger(Main.class);
    
    public static void main( String[] args ) throws Exception {
        setupLog4j(LOG_FILE);
    	redirectStreams(LOG_FILE);
    	
        String url = null;
        if (args.length != 2) {
        	throw new IllegalArgumentException(String.format("Expecting 2 arguments: --url $url"));
        }
        else {
        	// to be consistent with other implementations process accepts args:
        	// --url $url
        	// Also, we won't crazy with arg parsing/validation here
	        url = args[1];
        }
        logger.info(String.format("Starting jcmej via cxf on %s.", url));
        
        startCxf(url, cxfFctryBean);
    }
    
    static void redirectStreams(String fileName) throws Exception {
    	PrintStream ps = new PrintStream(new FileOutputStream(fileName));
    	System.setOut(ps);
    	System.setErr(ps);
    }
    
    static void setupLog4j(String logName) throws Exception {
        PatternLayout ptrnLayout = new PatternLayout("%d{ISO8601} %-5p [%t]: %m %n");
        DailyRollingFileAppender appender = new DailyRollingFileAppender(ptrnLayout, logName, "'.'yyyy-MM-dd");
        Logger logger = Logger.getRootLogger();
        logger.setLevel(LOG_LEVEL);
        logger.addAppender(appender);
    }
    
    static void startCxf(String url, JAXRSServerFactoryBean sf) {
    	sf.setResourceClasses(MatchingService.class);
    	sf.setResourceProvider(MatchingService.class, new SingletonResourceProvider(new MatchingService()));
    	sf.setAddress(url);
    	BindingFactoryManager manager = sf.getBus().getExtension(BindingFactoryManager.class);
    	JAXRSBindingFactory factory = new JAXRSBindingFactory();
    	factory.setBus(sf.getBus());
    	manager.registerBindingFactory(JAXRSBindingFactory.JAXRS_BINDING_ID, factory);
    	Server server = sf.create();
    	server.start();
    }
    
    /**
     * Gets home dir in portable way: HOME on *nix, HOMEPATH on windows
     * @return
     */
    static String getHomeDir() {
    	List<String> envVars = Arrays.asList(new String[] { "HOME", "HOMEPATH" });
    	for (String v : envVars) {
    		if (System.getenv(v) != null) {
    			return System.getenv(v);
    		}
    	}
    	throw new RuntimeException(String.format(
    		"Could not get home dir from os env vars %s.",
    		envVars));
    }
}
