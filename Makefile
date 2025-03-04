CLASS_NAMES = Node Message Clock

SOURCE_DIR = src
TARGET_DIR = build

SOURCE_FILES = $(addprefix $(SOURCE_DIR)/,$(addsuffix .java,$(CLASS_NAMES)))
TARGET_FILES = $(addprefix $(TARGET_DIR)/,$(addsuffix .class,$(CLASS_NAMES)))

NODE_IDS = 0 1 2 3


compile: $(SOURCE_FILES) $(TARGET_DIR)
	javac -d $(TARGET_DIR) $(SOURCE_FILES)

clean:
	rm -f $(TARGET_FILES)

$(NODE_IDS): $(TARGET_FILES)
	@clear
	@java -classpath $(TARGET_DIR) $(firstword $(CLASS_NAMES)) $@
