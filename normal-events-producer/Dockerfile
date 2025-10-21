FROM --platform=linux/amd64 public.ecr.aws/docker/library/python:3.9-slim

WORKDIR /app

RUN apt-get update && apt-get install -y \
    curl \
    telnet \
    dnsutils \
    awscli \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install -r requirements.txt

COPY data_generator.py .
COPY normal_events_producer.py .
COPY startup.sh .

RUN chmod +x startup.sh

CMD ["./startup.sh"]