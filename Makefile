data/heads-db.csv:
	curl https://raw.githubusercontent.com/TheLuca98/MinecraftHeads/refs/heads/master/heads.csv -o data/heads-database.csv

data/heads-db-b64.csv: data/heads-db.csv
	uv run data/add_base64_column.py data/heads-db.csv > data/heads-db-b64.csv


.PHONY: generate-mini-blocks
generate-mini-blocks: data/heads-db-b64.csv
	uv run data/generate_mini_blocks.py
	cp data/mini_blocks_GENERATED.yml headsmith/src/main/resources/heads/mini_blocks.yml
	rm -rf headsmith/src/main/resources/heads/alphabet
	mkdir -p headsmith/src/main/resources/heads/alphabet
	cp data/alphabet_GENERATED/*.yml headsmith/src/main/resources/heads/alphabet/

.PHONY: count-heads
count-heads:
	uv run data/count_heads.py

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
