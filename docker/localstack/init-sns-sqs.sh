#!/bin/sh
# LocalStack이 준비되면 실행된다: 로컬용 SNS 토픽·SQS 큐·구독을 만든다 (운영은 Terraform이 같은 구성을 만든다).
#  - SNS FIFO 토픽: 이벤트 종류마다 1개 (TOPIC 상수의 . 을 - 로)
#  - SQS FIFO 큐: 받는 서비스마다 1개 + DLQ(5번 실패하면 옮겨 둠)
#  - 구독: Raw 전달 (이벤트 봉투 JSON이 그대로 큐 메시지 본문이 된다)
# 서비스 레포와 nineouting-infra integration/에 같은 파일이 있다. 바꿀 때 함께 바꾼다.
set -e
PREFIX="${MESSAGING_NAME_PREFIX:-nineouting-local-}"

topic_arn() {
  awslocal sns create-topic --name "${PREFIX}$(echo "$1" | tr . -).fifo" \
    --attributes FifoTopic=true --query TopicArn --output text
}

# $1 = 서비스 이름, 나머지 = 받을 토픽
queue() {
  name="$1"; shift
  dlq_url=$(awslocal sqs create-queue --queue-name "${PREFIX}${name}-dlq.fifo" \
    --attributes FifoQueue=true,MessageRetentionPeriod=1209600 --query QueueUrl --output text)
  dlq_arn=$(awslocal sqs get-queue-attributes --queue-url "$dlq_url" --attribute-names QueueArn \
    --query Attributes.QueueArn --output text)
  url=$(awslocal sqs create-queue --queue-name "${PREFIX}${name}.fifo" \
    --attributes "{\"FifoQueue\":\"true\",\"VisibilityTimeout\":\"30\",\"MessageRetentionPeriod\":\"1209600\",\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${dlq_arn}\\\",\\\"maxReceiveCount\\\":\\\"5\\\"}\"}" \
    --query QueueUrl --output text)
  arn=$(awslocal sqs get-queue-attributes --queue-url "$url" --attribute-names QueueArn \
    --query Attributes.QueueArn --output text)
  for topic in "$@"; do
    awslocal sns subscribe --topic-arn "$(topic_arn "$topic")" --protocol sqs --notification-endpoint "$arn" \
      --attributes RawMessageDelivery=true > /dev/null
  done
  echo "queue ${name}: ${url}"
}

# 보내기만 하는 토픽도 미리 만든다 (없는 토픽으로 보내면 실패한다)
for topic in \
  user.email-verification-requested user.password-reset-requested \
  workspace.member-join-requested workspace.member-join-approved workspace.member-invited workspace.folder-deleted \
  meeting.created meeting.updated meeting.cancelled meeting.finished meeting.moved \
  minutes.review-requested minutes.confirmed minutes.edited; do
  topic_arn "$topic" > /dev/null
done

# 각 서비스 application.yml의 받는 이벤트와 같아야 한다
queue meeting-service workspace.folder-deleted minutes.review-requested minutes.confirmed
queue minutes-service meeting.finished meeting.moved workspace.folder-deleted
queue notification-service \
  user.email-verification-requested user.password-reset-requested \
  workspace.member-join-requested workspace.member-join-approved workspace.member-invited \
  meeting.created meeting.updated meeting.cancelled \
  minutes.review-requested minutes.confirmed minutes.edited

# 구독 없는 빈 큐 (시험용, 예: 여러 대 띄우기 시험에서 두 번째 대수가 이벤트를 받지 않게). 이름을 공백으로 구분
for name in ${MESSAGING_EXTRA_QUEUES:-}; do
  queue "$name"
done
