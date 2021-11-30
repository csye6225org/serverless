var aws = require("aws-sdk");
var ses = new aws.SES({ region: "us-east-1" });
var docClient = new aws.DynamoDB.DocumentClient();

exports.handler = function (event, context) {

    const tableName = "VaradDynamoDB";
    const sender_email = "no-reply@prod.varaddesai.me";
    const snsNotification = event.Records[0].Sns;
    const snsMessage = JSON.parse(snsNotification.Message);

    // Setting expiration time to 5 minutes
    const SECONDS_IN_5_MINS = 60 * 5;
    const secondsSinceEpoch = Math.round(Date.now() / 1000);
    const expirationTime = secondsSinceEpoch + SECONDS_IN_5_MINS;

    console.log("Creating Record for DynamoDb")

    var params = {
        TableName: tableName,
        Item: {
            "username":  snsMessage.username,
            "id": snsMessage.token_uuid,
            "ttl":  expirationTime
        }
    };

    console.log("Adding Record to DynamoDb")

    docClient.put(params, function(err, data) {
        if (err) {
            console.error("Unable to add record for", snsMessage.username, ". Error JSON:", JSON.stringify(err, null, 2));
        } else {
            console.log("PutItem succeeded:", snsMessage.username);
        }
    });

    console.log("Email Content");
    console.log(snsMessage.username);
    console.log(snsMessage.email_body);
    console.log(snsMessage.token_uuid);

    console.log("Sending Email");
    var params = {
        Destination: {
            ToAddresses: [snsMessage.username],
        },
        
        Message: {
            Body: {
                Html: { Data: snsMessage.email_body },
            },

            Subject: { Data: "Please verify your account" },
        },
        
        Source: sender_email,
    };
    return ses.sendEmail(params).promise()
};