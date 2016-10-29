package ar.edu.utn.frc.posgrado.jnonino;

import backtype.storm.Config;
import backtype.storm.StormSubmitter;
import backtype.storm.generated.AlreadyAliveException;
import backtype.storm.generated.InvalidTopologyException;
import backtype.storm.spout.SchemeAsMultiScheme;
import backtype.storm.topology.TopologyBuilder;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import storm.kafka.*;
import storm.kafka.trident.GlobalPartitionInformation;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Created by jnonino on 12/04/2016.
 */
public class Main {

    private static final Logger logger = LogManager.getLogger(Main.class);

    public static void main(String [] args) {

//        Properties props = new Properties();
//        String propsFileName = "config.properties";
//        InputStream inStream = Main.class.getClassLoader().getResourceAsStream(propsFileName);
//        try {
//            props.load(inStream);
//        } catch (IOException e) {
//            logger.error("No se han cargado las propiedades del sistema");
//            e.printStackTrace();
//        }

        String kafkaBrokerList = null;
        String topicToRead = null;

        if (args.length == 2) {
            kafkaBrokerList = args[0];
            topicToRead = args[1];
        } else {
            logger.error("Should run with two arguments");
            String usage = "Usage: storm jar storm-<VERSION>-jar-with-dependencies.jar <KAFKA_BROKER_LIST> <TOPIC_TO_READ>";
            logger.error(usage);
            System.exit(1);
        }

        GlobalPartitionInformation partitionInfo = new GlobalPartitionInformation();
        List<String> brokers = Arrays.asList(kafkaBrokerList.split(","));
        for(int index=0 ; index<brokers.size() ; index++) {
            Broker broker = new Broker(brokers.get(index));
            partitionInfo.addPartition(index, broker);
        }
        StaticHosts hosts = new StaticHosts(partitionInfo);

        SpoutConfig spoutConfig = new SpoutConfig(hosts, topicToRead, "/" + topicToRead, UUID.randomUUID().toString());
        spoutConfig.scheme = new SchemeAsMultiScheme(new StringScheme());
        KafkaSpout kafkaSpout = new KafkaSpout(spoutConfig);

        TopologyBuilder builder = new TopologyBuilder();
        builder.setSpout("kafkaSpout", kafkaSpout);
        builder.setBolt("processBolt", new DataProcessBolt()).shuffleGrouping("kafkaSpout");

        Config conf = new Config();

        // This sets the number of worker processes to use to execute the topology. For example, if you set this to 25,
        // there will be 25 Java processes across the cluster executing all the tasks.
        conf.setNumWorkers(1);

        // This sets the number of executors that will track tuple trees and detect when a spout tuple has been fully
        // processed. Ackers are an integral part of Storm's reliability model. By not setting this variable or setting
        // it as null, Storm will set the number of acker executors to be equal to the number of workers configured for
        // this topology. If this variable is set to 0, then Storm will immediately ack tuples as soon as they come off
        // the spout, effectively disabling reliability.
        conf.setNumAckers(1);

        // This sets the maximum number of spout tuples that can be pending on a single spout task at once (pending
        // means the tuple has not been acked or failed yet). It is highly recommended you set this config to prevent
        // queue explosion.
        conf.setMaxSpoutPending(2000);

        // This is the maximum amount of time a spout tuple has to be fully completed before it is considered failed.
        // This value defaults to 30 seconds, which is sufficient for most topologies.
        conf.setMessageTimeoutSecs(60);

        try {
            StormSubmitter.submitTopology("especialidad", conf, builder.createTopology());
        } catch (AlreadyAliveException e) {
            logger.error("Topology already running");
            e.printStackTrace();
        } catch (InvalidTopologyException e) {
            logger.error("Invalid Topology");
            e.printStackTrace();
        }
    }
}