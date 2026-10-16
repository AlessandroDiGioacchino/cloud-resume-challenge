
# Leaderboard & Write Sharding
https://www.dynamodbguide.com/leaderboard-write-sharding

## Schema Design: Primary Key and core attributes
First, we create the following table:

| Name      | Type    | Primary key |
| --------- | ------- | ----------- |
| Image     | String  | HASH        |
| Data      | Map     |             |
| ViewCount | Integer |             |

```
aws dynamodb create-table `
--table-name ImageLeaderboardTable `
--attribute-definitions '[
  {
    "AttributeName": "Image",
    "AttributeType": "S"
  }
]' `
--key-schema '[
  {
    "AttributeName": "Image",
    "KeyType": "HASH"
  }
]' `
--provisioned-throughput '{
  "ReadCapacityUnits": 1,
  "WriteCapacityUnits": 1
}'
```

  The following won't work, because DynamoDB expects the initial attribute
definitions to only contain primary key attributes, and an attribute of type
`Map` such as `Data` cannot be one:

<details>
  <summary>Doesn't work</summary>

  ```
  aws dynamodb create-table `
  --table-name ImageLeaderboardTable `
  --attribute-definitions '[
    {
      "AttributeName": "Image",
      "AttributeType": "S"
    },
    {
      "AttributeName": "Data",
      "AttributeType": "M"
    },
    {
      "AttributeName": "ViewCount",
      "AttributeType": "N"
    }
  ]' `
  --key-schema '[
    {
      "AttributeName": "Image",
      "KeyType": "HASH"
    }
  ]' `
  --provisioned-throughput '{
    "ReadCapacityUnits": 1,
    "WriteCapacityUnits": 1
  }'
  ```
</details>


## Adding a Leaderboard -- No Write-Sharding
Now we add a Global Secondary Index to achieve this schema:

| Name      | Type    | Primary Key | Leaderboard GSI |
| --------- | ------- | ----------- | --------------- |
| Image     | String  | HASH        |                 |
| Data      | Map     | --          |                 |
| ViewCount | Integer | --          | RANGE           |
| GSI Hash  | String  | --          | HASH            |

```
aws dynamodb update-table `
--table-name ImageLeaderboardTable `
--attribute-definitions '[
  {
    "AttributeName": "GSIHash",
    "AttributeType": "S"
  },
  {
    "AttributeName": "ViewCount",
    "AttributeType": "N"
  }
]' `
--global-secondary-index-updates '[
  {
    "Create": {
      "IndexName": "ViewCountGSIHashIndex",
      "KeySchema": [
        {
          "AttributeName": "GSI Hash",
          "KeyType": "HASH"
        },
        {
          "AttributeName": "ViewCount",
          "KeyType": "RANGE"
        }
      ],
      "Projection": {
        "ProjectionType": "ALL"
      },
      "ProvisionedThroughput": {
        "ReadCapacityUnits": 1,
        "WriteCapacityUnits": 1
      }
    }
  }
]'
```

  You may have added an element including `ViewCount` to the first table
version, but even so you need to include `ViewCount`, along with its type, to
the attribute definitions when creating the GSI; in other words, the following
won't work:

<details>
  <summary>Doesn't work.. probably</summary>

  ```
  aws dynamodb update-table `
  --table-name ImageLeaderboardTable `
  --attribute-definitions '[
    {
      "AttributeName": "GSI Hash",
      "AttributeType": "S"
    }
  ]
  --global-secondary-index-updates '[
    {
      "Create": {
        "IndexName": "ViewCountGSIHashIndex",
        "KeySchema": [
          {
            "AttributeName": "GSI Hash",
            "KeyType": "HASH"
          },
          {
            "AttributeName": "ViewCount",
            "KeyType": "RANGE"
          }
        ],
        "Projection": {
          "ProjectionType": "ALL"
        },
        "ProvisionedThroughput": {
          "ReadCapacityUnits": 1,
          "WriteCapacityUnits": 1
        }
      }
    }
  ]'
  ```
</details>

  Let's add some data to the table. You can find the command in
`BatchWriteItem.txt`, I won't put it here.

  Say we want the first three most viewed images. Composite primary keys are
already sorted by `RANGE` key, in ascending order unfortunately: if we were to
query the GSI for the first three items, we'd get the three least viewed
images. We need to reverse the sorting passing `no-scan-index-forward` to the
`query` command.

```
aws dynamodb query `
--table-name ImageLeaderboardTable `
--index-name ViewCountGSIHashIndex `
--key-condition-expression 'GSIHash = :gsihash' `
--expression-attribute-values '{
  ":gsihash": { "S": "IMAGES" }
}' `
--no-scan-index-forward `
--limit 3
```
