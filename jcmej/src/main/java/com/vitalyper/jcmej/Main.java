package com.vitalyper.jcmej;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
			getHomeDir(), "/jcmej.log");
	public static Level LOG_LEVEL = Level.DEBUG;
    static JAXRSServerFactoryBean cxfFctryBean = new JAXRSServerFactoryBean();
    static Logger logger = Logger.getLogger(Main.class);
    
    public static void main( String[] args ) throws Exception {
        setupLog4j(LOG_FILE);
    	
        Map<NamedParams, String> actualParams = parseParams(args);
        
        if (actualParams.size() != 2) {
        	throw new IllegalArgumentException(String.format(
        		"Expecting 4 arguments: --url $url --udp-port $udp-port Got %s.",
        	    Arrays.asList(args)));
        }
        
        String url = actualParams.get(NamedParams.url);
        logger.info(String.format("Starting jcmej via cxf on %s.", url));
        startCxf(url, cxfFctryBean);
        
        int udpPort = Integer.parseInt(actualParams.get(NamedParams.udpPort));
        sendStartedMsg(url, udpPort);
    }
    
    private static void sendStartedMsg(String url, int udpPort) throws IOException {
    	logger.info("Sleeping 2 secs");
    	try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	DatagramSocket socket = new DatagramSocket();
    	InetAddress localAddress = InetAddress.getLocalHost();
    	byte[] buf = url.getBytes();
    	
    	DatagramPacket packet = new DatagramPacket(buf, buf.length, localAddress, udpPort);
		socket.send(packet);
		socket.close();
        logger.info(String.format("Sent started msg %s to udp port %d.", url, udpPort));
	}

	static Map<NamedParams, String> parseParams(
			String[] args) {
		
		Map<NamedParams, String> out = new TreeMap<NamedParams, String>();
		for (int i=0; i < args.length; i++) {
			out.put(NamedParams.fromValue(args[i]), args[i + 1]);
			i++;
		}
		
		return out;
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
     * Gets home dir in portable way: HOME on *nix, USERPROFILE on windows
     * @return
     */
    static String getHomeDir() {
    	List<String> envVars = Arrays.asList(new String[] { "HOME", "USERPROFILE" });
    	for (String v : envVars) {
    		if (System.getenv(v) != null) {
    			return System.getenv(v);
    		}
    	}
    	throw new RuntimeException(String.format(
    		"Could not get home dir from os env vars %s.",
    		envVars));
    }
    
    
    enum NamedParams {
    	url("--url"),
    	udpPort("--udp-port");
    	
    	private String value;
    	NamedParams(String value) {
    		this.value = value;
    	}
    	
    	public String value() { 
    		return value; 
    	}
    	
		static NamedParams fromValue(String input) {
			for (NamedParams np : NamedParams.values()) {
				// Use case insensitive match
				if (np.value().equalsIgnoreCase(input)) {
					return np;
				}
			}
			throw new IllegalArgumentException(String.format(
				"Expected %s got %s.", 
				enumValuesToString(), input));
		}
		
	    static List<String> enumValuesToString() {
	    	List<String> out = new ArrayList<String>();
	    	for (NamedParams np : NamedParams.values()) {
	    		out.add(np.value());
	    	}
	    	return out;
	    }
    }
    
}
