package com.nineouting.backend.minutes;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

/**
 * 테스트용 LocalStack에 SNS FIFO 토픽, SQS FIFO 큐, 구독(Raw 전달)을 만든다. 운영에서는 Terraform이 만든다.
 * 이름 규칙은 운영과 같다: 토픽 {@code nineouting-test-meeting-created.fifo}, 큐 {@code nineouting-test-<이름>.fifo}.
 */
public class LocalMessaging {

    private static final String NAME_PREFIX = "nineouting-test-";
    /** 받는 쪽이 처리하지 못한 메시지가 다시 보이기까지 (테스트라 짧게) */
    private static final String VISIBILITY_TIMEOUT_SECONDS = "5";

    private final SnsClient sns;
    private final SqsClient sqs;
    private final Map<String, String> topicArns = new ConcurrentHashMap<>();

    public LocalMessaging(SnsClient sns, SqsClient sqs) {
        this.sns = sns;
        this.sqs = sqs;
    }

    /** outbox의 nineouting.outbox.topic-prefix 값 (토픽 ARN에서 이름을 뺀 앞부분) */
    public String topicArnPrefix(String anyTopic) {
        String arn = topicArn(anyTopic);
        return arn.substring(0, arn.lastIndexOf(':') + 1) + NAME_PREFIX;
    }

    /** 토픽이 없으면 만든다 */
    public String topicArn(String topic) {
        return topicArns.computeIfAbsent(topic, name -> sns.createTopic(request -> request
                .name(NAME_PREFIX + name.replace('.', '-') + ".fifo")
                .attributes(Map.of("FifoTopic", "true"))).topicArn());
    }

    /** 큐를 만들고 주어진 토픽들을 구독시킨다. 큐 주소를 돌려준다 */
    public String queue(String name, Collection<String> topics) {
        String url = sqs.createQueue(request -> request
                .queueName(NAME_PREFIX + name + ".fifo")
                .attributes(Map.of(QueueAttributeName.FIFO_QUEUE, "true",
                        QueueAttributeName.VISIBILITY_TIMEOUT, VISIBILITY_TIMEOUT_SECONDS))).queueUrl();
        String queueArn = sqs.getQueueAttributes(request -> request.queueUrl(url)
                .attributeNames(QueueAttributeName.QUEUE_ARN)).attributes().get(QueueAttributeName.QUEUE_ARN);
        for (String topic : topics) {
            sns.subscribe(request -> request.topicArn(topicArn(topic)).protocol("sqs").endpoint(queueArn)
                    .attributes(Map.of("RawMessageDelivery", "true")));
        }
        return url;
    }

    /** 다른 서비스가 보낸 것처럼 SNS에 넣는다 (outbox SnsOutboxSender와 같은 모양) */
    public void publish(String topic, String messageKey, String envelopeJson) {
        sns.publish(request -> request.topicArn(topicArn(topic))
                .message(envelopeJson)
                .messageGroupId(messageKey == null ? "default" : messageKey)
                .messageDeduplicationId(UUID.randomUUID().toString())
                .messageAttributes(Map.of("topic",
                        MessageAttributeValue.builder().dataType("String").stringValue(topic).build())));
    }

    /** 큐에서 조건에 맞는 메시지가 올 때까지 기다려 본문을 돌려준다. 받은 메시지는 지운다 */
    public String awaitMessage(String queueUrl, Duration wait, Predicate<String> matches) {
        Instant deadline = Instant.now().plus(wait);
        List<String> seen = new ArrayList<>();
        while (Instant.now().isBefore(deadline)) {
            for (Message message : sqs.receiveMessage(request -> request.queueUrl(queueUrl)
                    .waitTimeSeconds(1).maxNumberOfMessages(10)).messages()) {
                sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle()).build());
                if (matches.test(message.body())) {
                    return message.body();
                }
                seen.add(message.body());
            }
        }
        throw new AssertionError(queueUrl + "에서 조건에 맞는 메시지를 받지 못했습니다. 받은 메시지: " + seen);
    }
}
