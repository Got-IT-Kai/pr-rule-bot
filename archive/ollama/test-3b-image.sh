#!/bin/bash

echo "=== Cleaning up existing container ==="
docker stop ollama-test 2>/dev/null || true
docker rm ollama-test 2>/dev/null || true

echo ""
echo "=== Starting 3b image from GHCR ==="
docker run -d --name ollama-test -p 11435:11434 ghcr.io/got-it-kai/pr-rule-bot-ollama:3b

echo ""
echo "=== Waiting for Ollama to start ==="
sleep 10

echo ""
echo "=== Checking container logs for blobs ==="
docker logs ollama-test 2>&1 | grep "total blobs"

echo ""
echo "=== Checking available models ==="
MODELS=$(curl -s http://localhost:11435/api/tags | jq -r '.models[].name')
echo "Available models: $MODELS"

if echo "$MODELS" | grep -q "qwen2.5-coder:3b"; then
    echo "✅ Model qwen2.5-coder:3b found!"

    echo ""
    echo "=== Testing chat API ==="
    curl -s http://localhost:11435/api/chat -d '{
      "model": "qwen2.5-coder:3b",
      "messages": [{"role": "user", "content": "hello"}],
      "stream": false
    }' | jq .

    if [ $? -eq 0 ]; then
        echo "✅ Chat API works!"
    else
        echo "❌ Chat API failed!"
    fi
else
    echo "❌ Model qwen2.5-coder:3b NOT found!"
    echo "Container logs:"
    docker logs ollama-test
fi

echo ""
echo "=== Cleaning up ==="
docker stop ollama-test
docker rm ollama-test
