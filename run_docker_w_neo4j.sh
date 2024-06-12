#!/bin/sh

docker run \
    --name glomicave_w_neo4j \
    --rm \
    -it \
    -P \
    -v $HOME/.aws:/root/.aws \
    -v $(pwd)/src:/app/src \
    -v $(pwd)/data:/app/data \
    -v $(pwd)/config:/app/config \
    --network host \
    glomicave_kg_neo4j /bin/bash
