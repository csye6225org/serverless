import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.*;

import java.text.SimpleDateFormat;
import java.util.*;

public class SendEmail implements RequestHandler<SNSEvent, Object> {

    static DynamoDB dynamodb_client;
    private String dynamodb_table_name = "VaradDynamoDB";
    private Regions aws_region = Regions.US_EAST_1;
    public String from_email_address = "";
    static final String email_subject = "Verify Your Account";
    static String html_body;
    private static String text_body ="abc";
    private String body = "";
    static String token_to_send;
    static String username;
    List<String> sns_list;
    private long current_system_time;
    private long time_to_live;
    private long total_time_to_live;

    public Object handleRequest(SNSEvent request, Context context){
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        String Domain = System.getenv("domain_name");
        context.getLogger().log("Domain name is :" + Domain);
        from_email_address = "noreply@" + Domain;

        //Creating ttl
        context.getLogger().log("SNS Invocation started : " + timeStamp);
        current_system_time = Calendar.getInstance().getTimeInMillis() / 1000; // unix time
        time_to_live = 60 * Integer.parseInt(System.getenv("ttl")); // ttl set to 5 min
        total_time_to_live = time_to_live + current_system_time;

        try {
            sns_list = new ArrayList<String>(
                    Arrays.asList(request.getRecords().get(0).getSNS().getMessage().split(",")));
            username = sns_list.get(sns_list.size() - 1);
            sns_list.remove(sns_list.size() - 1);
        } catch (Exception e) {
            e.printStackTrace();
        }

        token_to_send = UUID.randomUUID().toString();
        context.getLogger().log("SNS Invocation completed : " + timeStamp);

        try {
            initDynamoDbClient();
            long ttl_db_value = 0;
            context.getLogger().log("Current timestamp " + timeStamp);
            if (this.dynamodb_client.getTable(dynamodb_table_name).getItem("id",username)!=null) {
                Item item = this.dynamodb_client.getTable(dynamodb_table_name).getItem("id", username);
                ttl_db_value = item.getLong("ttl");

                context.getLogger().log("ttl_db_value : " + ttl_db_value);
                context.getLogger().log("current ttl value : " + current_system_time);
                if (ttl_db_value <= current_system_time && ttl_db_value != 0) {
                    send_email(context);
                } else {
                    context.getLogger().log("ttl is not expired. New request will not be processed: " + username);
                }
            }
            else{
                context.getLogger().log("Calling send email");
                send_email(context);
            }
        } catch (Exception ex) {
            context.getLogger().log("Exception caught while sending email. Error message: " + ex.getMessage());
            send_email(context);
        }
        return null;
    }

    private void send_email(Context context) {

        context.getLogger().log("Checking dynamodb for valid ttl");
        context.getLogger().log("ttl is expired, creating new token");
        context.getLogger().log("sending email");
        this.dynamodb_client.getTable(dynamodb_table_name)
                .putItem(
                        new PutItemSpec().withItem(new Item()
                                .withString("id", username)
                                .withString("token", token_to_send)
                                .withLong("ttl", total_time_to_live)));

        for (String user : sns_list) {
            body = body + "<p>"
                    + "http://prod.varaddesai.me/v1/verifyUserEmail?email="
                    +sns_list.get(sns_list.size() - 1)
                    +"&token="
                    +token_to_send
                    + "</p>";
            context.getLogger().log(user);
        }
        html_body = "<h2>Email sent from Amazon SES</h2>"
                + body;
        context.getLogger().log("Email sent to  " + username);
        context.getLogger().log("This is HTML body: " + html_body);

        //Sending email using Amazon SES client
        AmazonSimpleEmailService clients = AmazonSimpleEmailServiceClientBuilder.standard().withRegion(aws_region).build();
        SendEmailRequest emailRequest = new SendEmailRequest()
                .withDestination(new Destination().withToAddresses(username))
                .withMessage(
                        new Message().withBody(
                                new Body().withHtml(
                                        new Content().withCharset("UTF-8").withData(html_body)
                                        ).withText(
                                                new Content().withCharset("UTF-8").withData(text_body)
                                            )
                                ).withSubject(new Content().withCharset("UTF-8").withData(email_subject)))
                .withSource(from_email_address);

        clients.sendEmail(emailRequest);

        context.getLogger().log("Email sent successfully to email id: " + username);

        body="";

        Stack<Integer> = new Stack
    }


    private void initDynamoDbClient() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().withRegion(aws_region).build();
        dynamodb_client = new DynamoDB(client);
    }
}
