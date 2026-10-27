package org.example;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ListTablesResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.TableNotFoundException;


public class Handler {
    private final DynamoDbClient dynamoDbClient;

    public Handler() {
        dynamoDbClient = DependencyFactory.dynamoDbClient();
    }

    public void sendRequest() {
        Logger logger = LoggerFactory.getLogger(Handler.class);

        logger.info("calling the DynamoDB API to get a list of existing tables");
        ListTablesResponse response = dynamoDbClient.listTables();

        if (!response.hasTableNames()) {
            logger.info("No existing tables found for the configured account & region");
        } else {
            response.tableNames().forEach(tableName -> logger.info("Table: " + tableName));
        }

        List<String> partitionKeysValue = new ArrayList<>();

        partitionKeysValue.add( "PARTITION_0" );
        partitionKeysValue.add( "PARTITION_1" );
        partitionKeysValue.add( "PARTITION_2" );

        final List<Map<String, AttributeValue>> res = queryPartitions(
          "ImageLeaderboardTable",
          "ViewCountGSIHashIndex",
          partitionKeysValue,
          logger
        );

        logger.info( " Partition\t ViewCount\t\t Image");

        res.forEach( item -> logger.info(
          item.get( "GSIHash" ).s()
          + "\t\t" + item.get( "ViewCount" ).n()
          + "\t" + item.get( "Image" ).s()
        ) );

        // response2.items().forEach( item -> logger.info( "item: " + item ) );
    }

    /**
     *   Queries a DynamoDB Global Secondary Index (GSI) by a list of partition
     * keys.
     *
     * @param tableName          The name of the DynamoDB table
     * @param indexName          The name of the GSI
     * @param partitionKeysValue The value of the GSI partition keys to query
     * @param logger             The logger where to print infos and errors
     * @return The query response from DynamoDB
     * @throws ResourceNotFoundException if the table or index doesn't exist
     * @throws DynamoDbException if the query fails
     */
    public List<Map<String, AttributeValue>> queryPartitions(
      final String tableName, final String indexName,
      final List<String> partitionKeysValue, final Logger logger
    ) {

      List<Map<String, AttributeValue>> gatheredResponse = new ArrayList<>();

      for ( final String partitionKeyValue : partitionKeysValue ) {
        final QueryResponse response = queryGlobalSecondaryIndex(
          tableName, indexName, partitionKeyValue, logger
        );

        gatheredResponse.addAll( response.items() );
      }

      gatheredResponse.sort( new Comparator<Map<String, AttributeValue>>() {
        public int compare(
          Map<String, AttributeValue> o1, Map<String, AttributeValue> o2
        ) {

          final Integer firstViewCount = Integer.valueOf(
            o1.get( "ViewCount" ).n()
          );

          final Integer secondViewCount = Integer.valueOf(
            o2.get( "ViewCount" ).n()
          );

          return firstViewCount.compareTo( secondViewCount );
        }
      } );

      for (
        final Map<String, AttributeValue> attribute :
        gatheredResponse.reversed()
      ) {

        logger.info( attribute.toString() );
      }

      return gatheredResponse.reversed();
    }

    /**
     * Queries a DynamoDB Global Secondary Index (GSI) by partition key.
     *
     * @param tableName         The name of the DynamoDB table
     * @param indexName         The name of the GSI
     * @param partitionKeyValue The value of the GSI partition key to query
     * @param logger            The logger where to print infos and errors
     * @return The query response from DynamoDB
     * @throws ResourceNotFoundException if the table or index doesn't exist
     * @throws DynamoDbException if the query fails
     */
    public QueryResponse queryGlobalSecondaryIndex(
      final String tableName, final String indexName,
      final String partitionKeyValue, final Logger logger
    ) {

      final Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
      expressionAttributeValues.put(
        ":partition", AttributeValue.builder().s( partitionKeyValue ).build()
      );

      final QueryRequest queryRequest = QueryRequest.builder()
        .tableName( tableName )
        .indexName( indexName )
        .keyConditionExpression( "GSIHash = :partition" )
        .expressionAttributeValues( expressionAttributeValues )
        .scanIndexForward( false )
        .limit( 3 )
        .build();

      try {
        final QueryResponse response = dynamoDbClient.query(queryRequest);
        logger.info(
          "Query on GSI successful. Found " + response.count() + " items"
        );

        return response;
      } catch ( ResourceNotFoundException e ) {
        logger.error(
          "Error: The Amazon DynamoDB table " + tableName + " or index "
            + indexName + " can't be found."
        );

        throw TableNotFoundException.builder()
          .message( "Table or index not found: " + tableName + "/" + indexName )
          .cause( e )
          .build();
      } catch ( DynamoDbException e ) {
        logger.error( "Error querying GSI: " + e.getMessage() );
        throw DynamoDbException.builder()
          .message( "Failed to execute query on GSI" )
          .cause( e )
          .build();
      }

    }
}
