.PHONY: generate-mini-blocks
generate-mini-blocks:
	cd craftheads/data && uv run generate_mini_blocks.py
	cp craftheads/data/mini_blocks_GENERATED.yml craftheads/src/main/resources/heads/mini_blocks.yml
	rm -rf craftheads/src/main/resources/heads/alphabet
	mkdir -p craftheads/src/main/resources/heads/alphabet
	cp craftheads/data/alphabet_GENERATED/*.yml craftheads/src/main/resources/heads/alphabet/

.PHONY: count-heads
count-heads:
	uv run craftheads/data/count_heads.py

.PHONY: build
build: generate-mini-blocks count-heads
	cd craftheads && gradle shadowJar
	cp craftheads/build/libs/CraftHeads.jar bin/


.PHONY: server
server: build
	rm -f server/plugins/CraftHeads*.jar
	rm -rf server/plugins/CraftHeads/
	cp craftheads/build/libs/*.jar server/plugins/
	cd server && java -Xmx2G -Xms2G -jar paper-1.21.10-105.jar nogui

.PHONY: clean
clean:
	gradle clean
