package com.github.bliblofy.producecustomer;

import com.google.common.collect.Lists;
import com.twitter.hbc.ClientBuilder;
import com.twitter.hbc.core.Client;
import com.twitter.hbc.core.Constants;
import com.twitter.hbc.core.Hosts;
import com.twitter.hbc.core.HttpHosts;
import com.twitter.hbc.core.endpoint.StatusesFilterEndpoint;
import com.twitter.hbc.core.processor.StringDelimitedProcessor;
import com.twitter.hbc.httpclient.auth.Authentication;
import com.twitter.hbc.httpclient.auth.OAuth1;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

 public class ProducerCustomer {

        Logger logger = LoggerFactory.getLogger(ProducerCustomer.class.getName());


        String consumerKey = "your credentials";
        String consumerSecret = "your credentials";
        String token = "your credentials";
        String secret = "your credentials";

        //Hier kann der Hashtag geändert werden, nach dem gesucht werden soll.
        // ----->
        // ----->
        List<String> terms = Lists.newArrayList("corona", "homeoffice");


        public ProducerCustomer(){}

        public static void main(String[] args) {
            new ProducerCustomer().run();
        }

        public void run(){

            logger.info("Setup");

            /** Set up your blocking queues: Be sure to size these properly based on expected TPS of your stream */
            BlockingQueue<String> msgQueue = new LinkedBlockingQueue<String>(1000);

            // Erstellung twitter client
            //ACHTUNG: Es muss der Twitter Client als dependency verwendet werden, nicht der JDK Safe!
            Client client = createTwitterClient(msgQueue);

            // Herstellen der connection zu Twitter.
            client.connect();

            // Erstellung vom Producer

            KafkaProducer<String, String> producer = createKafkaProducer();

            // shutdown zum Abschalten ohne Fehlermeldung

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("stopping application...");
                logger.info("shutting down client from twitter...");
                client.stop();
                logger.info("closing producer...");
                producer.close();
                logger.info("done!");
            }));

            // loop um Tweets wiederholt zu senden
            while (!client.isDone()) {
                String msg = null;
                try {
                    // Timeout damit der Kafka nicht mit Nachrichten zugespamt wird

                    msg = msgQueue.poll(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    client.stop();
                }
                if (msg != null){
                    logger.info(msg);

                    // Hier im Beispiel ohne Keys, es wäre aber möglich über eine weitere Funktion Keys einzufügen.

                    producer.send(new ProducerRecord<>("twitter_tweets", null, msg), new Callback() {
                        @Override
                        public void onCompletion(RecordMetadata recordMetadata, Exception e) {
                            if (e != null) {
                                logger.error("Error, läuft nicht wie gedacht, auf zur Fehlersuche", e);
                            }
                        }
                    });
                }
            }
            logger.info("Ende des Programms");
        }

        public Client createTwitterClient(BlockingQueue<String> msgQueue){

            // Definitionen aus der definition von Twitter zur Twitter API

            /** Declare the host you want to connect to, the endpoint, and authentication (basic auth or oauth) */
            Hosts hosebirdHosts = new HttpHosts(Constants.STREAM_HOST);
            StatusesFilterEndpoint hosebirdEndpoint = new StatusesFilterEndpoint();

            hosebirdEndpoint.trackTerms(terms);

            // These secrets should be read from a config file
            Authentication hosebirdAuth = new OAuth1(consumerKey, consumerSecret, token, secret);

            ClientBuilder builder = new ClientBuilder()
                    .name("Hosebird-Client-01")                              // optional: mainly for the logs
                    .hosts(hosebirdHosts)
                    .authentication(hosebirdAuth)
                    .endpoint(hosebirdEndpoint)
                    .processor(new StringDelimitedProcessor(msgQueue));

            Client hosebirdClient = builder.build();
            return hosebirdClient;
        }

        public KafkaProducer<String, String> createKafkaProducer(){
            String bootstrapServers = "127.0.0.1:9092";

            // create Producer properties diese sind am einfachsten in der Kafka Doku erklärt
            //https://kafka.apache.org/documentation/#producerapi
            Properties properties = new Properties();
            properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

            // erstelle einen safe Producer
            // Ebenfalls in der Doku.
            properties.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
            properties.setProperty(ProducerConfig.ACKS_CONFIG, "all");
            properties.setProperty(ProducerConfig.RETRIES_CONFIG, Integer.toString(Integer.MAX_VALUE));
            properties.setProperty(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5"); // kafka 2.0 >= 1.1 so we can keep this as 5. Use 1 otherwise.

            // high throughput producer (at the expense of a bit of latency and CPU usage)
            properties.setProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
            properties.setProperty(ProducerConfig.LINGER_MS_CONFIG, "20");
            properties.setProperty(ProducerConfig.BATCH_SIZE_CONFIG, Integer.toString(32*1024)); // 32 KB batch size

            // create the producer
            KafkaProducer<String, String> producer = new KafkaProducer<String, String>(properties);
            return producer;
        }
    }
