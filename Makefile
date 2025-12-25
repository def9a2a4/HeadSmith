.PHONY: generate-mini-blocks
generate-mini-blocks:
	cd headsmith/data && uv run generate_mini_blocks.py
	cp headsmith/data/mini_blocks_GENERATED.yml headsmith/src/main/resources/heads/mini_blocks.yml
	rm -rf headsmith/src/main/resources/heads/alphabet
	mkdir -p headsmith/src/main/resources/heads/alphabet
	cp headsmith/data/alphabet_GENERATED/*.yml headsmith/src/main/resources/heads/alphabet/

.PHONY: count-heads
count-heads:
	uv run headsmith/data/count_heads.py

.PHONY: build
build: generate-mini-blocks count-heads
	cd headsmith && gradle shadowJar
	cp headsmith/build/libs/HeadSmith*.jar bin/


.PHONY: server
server: build
	rm -f server/plugins/HeadSmith*.jar
	rm -rf server/plugins/headsmith/
	cp headsmith/build/libs/*.jar server/plugins/
	cd server && java -Xmx2G -Xms2G -jar paper-1.21.10-105.jar nogui

.PHONY: clean
clean:
	gradle clean
