package com.daml.quickstart.iou;

import akka.actor.ActorSystem;
import akka.grpc.GrpcClientSettings;
import com.daml.ledger.api.auth.client.LedgerCallCredentials;
import com.daml.ledger.javaapi.data.CreatedEvent;
import com.daml.ledger.javaapi.data.Event;
import com.daml.lf.codegen.json.JsonCodec;
import com.daml.projection.*;
import com.daml.projection.javadsl.BatchSource;
import com.daml.projection.javadsl.Control;
import com.daml.projection.javadsl.Projector;
import com.daml.quickstart.model.iou.Iou;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

public class BatchedProjectionRunner {
	private static final Logger logger = LoggerFactory.getLogger(BatchedProjectionRunner.class);

	public static void main(String[] args) {

		if (args.length < 1)
			throw new IllegalArgumentException("An argument for party of Alice is expected.");

		var aliceParty = args[0];

		// Setup db params
		String url = "jdbc:postgresql://localhost/ious";
		String user = "postgres";
		String password = "postgres";

		// create actor system used by projector and grpc client
		ActorSystem system = ActorSystem.create("iou-projection");

		// setup datasource and projection table
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(url);
		config.setUsername(user);
		config.setPassword(password);
		DataSource dataSource = new HikariDataSource(config);
		ProjectionTable projectionTable = new ProjectionTable("events");

		GrpcClientSettings grpcClientSettings = GrpcClientSettings
				.connectToServiceAt("localhost", 6865, system)
				.withTls(false);

		Projection contracts = Projection.<Iou.Contract>create(new ProjectionId("active-iou-contracts-for-alice"), ProjectionFilter.parties(Set.of(aliceParty)));

		BatchSource batchSource = BatchSource.create(grpcClientSettings,
				event -> {
					logger.info("Projecting event " + event.getEventId());
					return Iou.Contract.fromCreatedEvent(event);
				});

		Project<Iou.Contract, Iou.Contract> mkRow =
				envelope -> {
					return List.of(envelope.getEvent());
				};

		var jsonCodec = JsonCodec.encodeAsNumbers();

		Projector<JdbcAction> projector = JdbcProjector.create(dataSource, system);
		Binder<Iou.Contract> binder = Sql.<Iou.Contract>binder(
						"insert into "
								+ projectionTable.getName()
								+ "(contract_id, event_id, amount, currency, json_data) "
								+ "values (?, ?, ?, ?, ?::jsonb)")
				.bind(1, iou -> iou.id.contractId, Bind.String())
				.bind(2, iou -> iou.id.contractId, Bind.String())
				.bind(3, iou -> iou.data.amount, Bind.BigDecimal())
				.bind(4, iou -> iou.data.currency, Bind.String())
				.bind(5, iou -> jsonCodec.toJsValue(iou.data.toValue()).compactPrint(), Bind.String());

		BatchRows<Iou.Contract, JdbcAction> batchRows = UpdateMany.create(binder);

		logger.info("Starting projection...");

		Control control = projector.projectRows(batchSource, contracts, batchRows, mkRow);

		control.failed().whenComplete((throwable, ignored) -> {
			if (throwable instanceof NoSuchElementException) {
				logger.info("Projection finished");
			} else {
				logger.error("Failed to run projection", throwable);
			}

			control.resourcesClosed().thenRun(system::terminate);
		});
	}
}
