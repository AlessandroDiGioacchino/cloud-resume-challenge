
# CRC Step 4: Static S3 website
https://docs.aws.amazon.com/AmazonS3/latest/userguide/HostingWebsiteOnS3Setup.html

## Step 4A: Create an S3 bucket
  `--create-bucket-configuration LocationConstraint=eu-south-1` is needed when
the bucket is being created in some region other than `us-east-1`.

- The command fails if `LocationConstraint` is not specified: why not populate
  it automatically from `--region`?
- What happens if `--region != LocationConstraint`?

```
aws s3api create-bucket \
  --bucket cloudresumechallenge-836064768589-eu-south-1-an \
  --region eu-south-1 \
  --create-bucket-configuration LocationConstraint=eu-south-1
```

To confirm the bucket was created successfully,
```
aws s3 ls
```

The output should be something like
```
2026-09-20 16:58:20 cloudresumechallenge-836064768589-eu-south-1-an
```

The date and time on the left represent when the bucket was created.


## Step 4B: Enable static website hosting

```
aws s3 website s3://cloudresumechallenge-836064768589-eu-south-1-an \
  --index-document cv.html
```


## Step 4C: Edit Block Public Access settings
```
aws s3api delete-public-access-block  \
  -- bucket cloudresumechallenge-836064768589-eu-south-1-an
```


## Step 4D: Add a bucket policy that makes your bucket content publicly available
```
aws s3api put-bucket-policy \
  --bucket cloudresumechallenge-836064768589-eu-south-1-an/* \
  --policy '{
    "Version": "2012-10-17",
    "Statement": [
      {
        "Sid": "PublicReadGetObject",
        "Effect": "Allow",
        "Principal": "*",
        "Action": "s3:GetObject",
        "Resource": "arn:aws:s3:::cloudresumechallenge-836064768589-eu-south-1-an/*"
      }
    ]
  }'
```

Remember that the first byte of the policy must be `{`
The following won't work, because the first byte is a newline character:
```
aws s3api put-bucket-policy \
  --bucket cloudresumechallenge-836064768589-eu-south-1-an/*
  --policy '
  {
    "Version": "2012-10-17",
    "Statement": [
      {
        "Sid": "PublicReadGetObject",
        "Effect": "Allow",
        "Principal": "*",
        "Action": "s3:GetObject",
        "Resource": "arn:aws:s3:::cloudresumechallenge-836064768589-eu-south-1-an/*"
      }
    ]
  }
  '
```

## Step 4E: Configure an index document
  This is Step 2 and Step 3 of CRC, plus the following to actually upload HTML
and CSS.
```
aws s3 cp 'cv.html' s3://cloudresumechallenge-836064768589-eu-south-1-an
aws s3 cp 'altacv.css' s3://cloudresumechallenge-836064768589-eu-south-1-an
```

## Step 4F: Configure an error document
Can be skipped.

## Step 4G: Test your website endpoint
The website endpoint is always:
```
http://<bucket_name>.s3-website.<region>.amazonaws.com
```

http://cloudresumechallenge-836064768589-eu-south-1-an.s3-website.eu-south-1.amazonaws.com
