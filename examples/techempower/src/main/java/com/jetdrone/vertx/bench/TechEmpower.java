package com.jetdrone.vertx.bench;

import com.jetdrone.vertx.yoke.Middleware;
import com.jetdrone.vertx.yoke.Yoke;
import com.jetdrone.vertx.yoke.middleware.BodyParser;
import com.jetdrone.vertx.yoke.middleware.Router;
import com.jetdrone.vertx.yoke.middleware.YokeRequest;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.Handler;
import org.vertx.java.platform.Verticle;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class TechEmpower extends Verticle {

    @Override
    public void start() {

        final EventBus eb = vertx.eventBus();
        final String address = "bench.mongodb";

        JsonObject dbConfig = new JsonObject()
                .putString("address", address)
                .putString("db_name", "hello_world")
                .putString("host", "'localhost'");

        // deploy mongo module
        container.deployModule("io.vertx~mod-mongo-persistor~2.0.0-CR2", dbConfig);

        // create the yoke app
        new Yoke(vertx)
                // JSON serialization
                .use("/json", new Middleware() {
                    @Override
                    public void handle(YokeRequest request, Handler<Object> next) {
                        // For each request, an object mapping the key message to Hello, World! must be instantiated.
                        request.response().end(new JsonObject().putString("message", "Hello, World!"));
                    }
                })
                // plain text
                .use("/plaintext", new Middleware() {
                    @Override
                    public void handle(YokeRequest request, Handler<Object> next) {
                        request.response().setContentType("text/plain");
                        // Write plaintext "Hello, World!" to the response.
                        request.response().end("Hello, World!");
                    }
                })
                // db
                .use("/db", new Middleware() {
                    @Override
                    public void handle(final YokeRequest request, Handler<Object> next) {

                        final Random random = ThreadLocalRandom.current();

                        int param = 1;
                        try {
                            param = Integer.parseInt(request.params().get("queries"));

                            // Bounds check.
                            if (param > 500) {
                                param = 500;
                            }
                            if (param < 1) {
                                param = 1;
                            }
                        } catch (NumberFormatException nfexc) {
                            // do nothing
                        }

                        // Get the count of queries to run.
                        final int count = param;

                        final Handler<Message<JsonObject>> dbh = new Handler<Message<JsonObject>>() {
                            // how many messages have this handler received
                            int received = 0;
                            // keeps the received messages
                            JsonArray result = new JsonArray();

                            @Override
                            public void handle(Message<JsonObject> message) {
                                // increase the counter
                                received++;

                                // get the body
                                final JsonObject body = message.body();

                                if ("ok".equals(body.getString("status"))) {
                                    // json -> string serialization
                                    result.add(body.getObject("result"));
                                }

                                // end condition
                                if (received == count) {
                                    request.response().end(result);
                                }
                            }
                        };

                        for (int i = 0; i < count; i++) {
                            eb.send(
                                    address,
                                    new JsonObject()
                                            .putString("action", "findone")
                                            .putString("collection", "world")
                                            .putObject("matcher", new JsonObject().putNumber("id", (random.nextInt(10000) + 1))),
                                    dbh);
                        }
                    }
                })
                .listen(8080);
    }
}