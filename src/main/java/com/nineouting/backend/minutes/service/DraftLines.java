package com.nineouting.backend.minutes.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * AI 초안의 줄 목록 (ai-service docs/api/minutes-draft-contract.md 5.5, 5.7, 8장).
 * <ul>
 *   <li>결과 콜백의 줄(글 칸은 줄, 표 칸은 행)마다 근거, 출처, 고친 표시를 저장해 둔다</li>
 *   <li>양식을 바꿀 때 지금 칸의 글과 저장해 둔 줄을 맞춰 다시 배치의 이전 줄(previousLines)을 만든다.
 *       글이 그대로인 줄은 저장한 값을 쓰고, 같은 칸에서 고친 줄은 출처를 이어받아 draftEdited를 켜고,
 *       맞는 줄이 없으면 작성자가 새로 쓴 줄로 본다. 저장해 둔 줄 중 칸에 없는 것은 작성자가 지운 것이라 보내지 않는다</li>
 *   <li>ai-service는 칸 글을 묶어 쓸 수 있다: "- {묶음 제목}" 줄 아래 들여쓴 "  - {글}" 줄. 묶음 제목 줄은 줄이 아니고,
 *       안건으로 묶으면 아래 줄에서 "{안건 제목}: "을 뗀다 (ai-service minutes/lines.py content_of)</li>
 * </ul>
 */
final class DraftLines {

    /** AI가 채울 내용이 없는 칸의 글 (명세 5.4) */
    static final String NOTHING = "해당 내용 없음";
    /** 명세 7.5 상한 */
    static final int MAX_PREVIOUS_LINES = 600;
    static final int MAX_LINE_LENGTH = 2000;

    private static final String TEXT = "text";
    private static final String TABLE = "table";
    private static final String BULLET = "- ";
    private static final String CELL_SEPARATOR = "\t";
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    /** 양식을 바꾸기 전의 칸 */
    record Section(String title, String content, boolean table) {
    }

    private DraftLines() {
    }

    /** 결과 콜백의 sections 중 titles에 든 칸의 줄을 저장 형식으로 꺼낸다 */
    static List<ObjectNode> fromResult(JsonNode sections, Set<String> titles) {
        List<ObjectNode> lines = new ArrayList<>();
        for (JsonNode section : sections) {
            String title = section.path("title").asString("");
            if (!titles.contains(title)) {
                continue;
            }
            boolean table = TABLE.equals(section.path("type").asString(""));
            for (JsonNode evidence : section.path("evidence")) {
                ObjectNode line = NODES.objectNode();
                line.put("title", title);
                line.put("type", table ? TABLE : TEXT);
                if (table) {
                    ArrayNode cells = line.putArray("cells");
                    evidence.path("cells").forEach(cell -> cells.add(cell.asString("").strip()));
                } else {
                    line.put("sentence", evidence.path("sentence").asString("").strip());
                }
                line.set("transcriptSeqs", array(evidence.path("transcriptSeqs")));
                line.put("edited", evidence.path("edited").asBoolean(false));
                line.put("needsCheck", evidence.path("needsCheck").asBoolean(false));
                line.set("sources", array(evidence.path("sources")));
                line.put("draftEdited", evidence.path("draftEdited").asBoolean(false));
                lines.add(line);
            }
        }
        return lines;
    }

    /** 저장된 줄 중 titles 칸의 줄을 새 줄로 바꾼다 (초안이 채운 칸만 바뀐다) */
    static ArrayNode replace(JsonNode stored, Set<String> titles, List<ObjectNode> lines) {
        ArrayNode merged = NODES.arrayNode();
        if (stored != null) {
            stored.forEach(line -> {
                if (!titles.contains(line.path("title").asString(""))) {
                    merged.add(line);
                }
            });
        }
        lines.forEach(merged::add);
        return merged;
    }

    /**
     * 양식을 바꾸기 전 칸의 글과 저장해 둔 줄로 다시 배치의 이전 줄을 만든다.
     * 상한(600줄)을 넘으면 null (다시 배치를 하지 않고 제목이 같은 칸의 글만 옮긴다)
     */
    static ArrayNode previousLines(List<Section> sections, JsonNode stored) {
        List<JsonNode> storedLines = new ArrayList<>();
        if (stored != null) {
            stored.forEach(storedLines::add);
        }
        boolean[] used = new boolean[storedLines.size()];
        Map<String, List<Integer>> byKey = new HashMap<>();
        for (int index = 0; index < storedLines.size(); index++) {
            byKey.computeIfAbsent(key(storedLines.get(index)), ignored -> new ArrayList<>()).add(index);
        }

        // 칸마다 지금 줄과 맞춘 저장 줄 번호 (-1이면 아직 못 맞춤)
        List<List<ObjectNode>> current = new ArrayList<>();
        List<List<Integer>> matched = new ArrayList<>();
        for (Section section : sections) {
            List<ObjectNode> lines = section.table() ? rows(section) : sentences(section);
            current.add(lines);
            List<Integer> found = new ArrayList<>();
            for (ObjectNode line : lines) {
                int index = -1;
                for (String key : keys(line)) {
                    index = takeExact(byKey.get(key), storedLines, used, section.title());
                    if (index >= 0) {
                        break;
                    }
                }
                found.add(index);
            }
            matched.add(found);
        }
        // 같은 칸에서 못 맞춘 줄 수가 같으면 차례대로 고친 줄로 짝짓는다
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            Section section = sections.get(sectionIndex);
            List<Integer> found = matched.get(sectionIndex);
            List<Integer> leftover = new ArrayList<>();
            for (int index = 0; index < storedLines.size(); index++) {
                if (!used[index] && section.title().equals(storedLines.get(index).path("title").asString(""))) {
                    leftover.add(index);
                }
            }
            long unmatched = found.stream().filter(index -> index < 0).count();
            if (unmatched == 0 || unmatched != leftover.size()) {
                continue;
            }
            int next = 0;
            for (int position = 0; position < found.size(); position++) {
                if (found.get(position) < 0) {
                    int index = leftover.get(next++);
                    used[index] = true;
                    found.set(position, -2 - index);
                }
            }
        }

        ArrayNode previous = NODES.arrayNode();
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            String title = sections.get(sectionIndex).title();
            List<ObjectNode> lines = current.get(sectionIndex);
            List<Integer> found = matched.get(sectionIndex);
            for (int position = 0; position < lines.size(); position++) {
                int index = found.get(position);
                JsonNode source = index >= 0 ? storedLines.get(index) : index <= -2 ? storedLines.get(-2 - index) : null;
                boolean draftEdited = source == null || index <= -2 || source.path("draftEdited").asBoolean(false);
                // 그대로인 줄은 저장한 글 (묶음에서 뗀 안건 제목까지), 고쳤거나 새로 쓴 줄은 지금 글
                String sentence = index >= 0 && source.has("sentence") ? source.path("sentence").asString("")
                        : sentence(lines.get(position), source);
                previous.add(previousLine(title, sentence, source, draftEdited));
            }
        }
        return previous.size() > MAX_PREVIOUS_LINES ? null : previous;
    }

    /** 글을 그대로 둔 줄: 같은 칸의 것을 먼저 쓴다 */
    private static int takeExact(List<Integer> candidates, List<JsonNode> storedLines, boolean[] used, String title) {
        if (candidates == null) {
            return -1;
        }
        int fallback = -1;
        for (int index : candidates) {
            if (used[index]) {
                continue;
            }
            if (title.equals(storedLines.get(index).path("title").asString(""))) {
                used[index] = true;
                return index;
            }
            if (fallback < 0) {
                fallback = index;
            }
        }
        if (fallback >= 0) {
            used[fallback] = true;
        }
        return fallback;
    }

    private static ObjectNode previousLine(String title, String sentence, JsonNode source, boolean draftEdited) {
        ObjectNode line = NODES.objectNode();
        line.put("title", title);
        String flat = sentence.replace('\n', ' ').strip();
        line.put("sentence", flat.length() > MAX_LINE_LENGTH ? flat.substring(0, MAX_LINE_LENGTH) : flat);
        line.set("transcriptSeqs", source == null ? NODES.arrayNode() : array(source.path("transcriptSeqs")));
        line.put("edited", source != null && source.path("edited").asBoolean(false));
        line.put("needsCheck", source != null && source.path("needsCheck").asBoolean(false));
        line.set("sources", source == null ? NODES.arrayNode() : array(source.path("sources")));
        line.put("draftEdited", draftEdited);
        return line;
    }

    /**
     * 글 칸: 한 줄이 줄 하나. 앞의 "- "는 뗀다. 들여쓴 줄이 바로 아래에 오는 들여쓰지 않은 줄은 묶음 제목이라
     * 줄로 세지 않고, 아래 줄에 heading으로 붙인다
     */
    private static List<ObjectNode> sentences(Section section) {
        List<ObjectNode> lines = new ArrayList<>();
        String[] raws = section.content().split("\n");
        String heading = null;
        for (int index = 0; index < raws.length; index++) {
            String raw = raws[index];
            boolean indented = !raw.isEmpty() && Character.isWhitespace(raw.charAt(0));
            String text = withoutBullet(raw);
            if (text.isEmpty() || NOTHING.equals(text)) {
                continue;
            }
            if (!indented) {
                heading = null;
                if (index + 1 < raws.length && isIndentedBullet(raws[index + 1])) {
                    heading = text;
                    continue;
                }
            }
            ObjectNode line = NODES.objectNode().put("type", TEXT).put("sentence", text);
            if (indented && heading != null) {
                line.put("heading", heading);
            }
            lines.add(line);
        }
        return lines;
    }

    private static String withoutBullet(String raw) {
        String text = raw.strip();
        return text.startsWith(BULLET) ? text.substring(BULLET.length()).strip() : text;
    }

    private static boolean isIndentedBullet(String raw) {
        return !raw.isEmpty() && Character.isWhitespace(raw.charAt(0)) && raw.strip().startsWith(BULLET);
    }

    /** 지금 줄이 저장 줄과 같은지 견줄 값들: 글 그대로, 안건으로 묶였으면 "{묶음 제목}: {글}"도 */
    private static List<String> keys(JsonNode line) {
        if (line.has("heading")) {
            return List.of(key(line), TEXT + "\n" + line.path("heading").asString("") + ": " + line.path("sentence").asString(""));
        }
        return List.of(key(line));
    }

    /** 표 칸 (저장 형식: 줄 = 행, 탭 = 열, 첫 행은 머리 행): 머리 행 아래 행 하나가 줄 하나 */
    private static List<ObjectNode> rows(Section section) {
        List<ObjectNode> lines = new ArrayList<>();
        String[] rows = section.content().split("\n");
        for (int index = 1; index < rows.length; index++) {
            ObjectNode line = NODES.objectNode().put("type", TABLE);
            ArrayNode cells = line.putArray("cells");
            boolean empty = true;
            for (String cell : rows[index].split(CELL_SEPARATOR, -1)) {
                cells.add(cell.strip());
                empty &= cell.isBlank();
            }
            if (!empty) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** 같은 줄인지 견주는 값: 글 칸은 글, 표 칸은 칸 글 */
    private static String key(JsonNode line) {
        if (TABLE.equals(line.path("type").asString(""))) {
            List<String> cells = new ArrayList<>();
            line.path("cells").forEach(cell -> cells.add(cell.asString("")));
            return TABLE + "\n" + String.join(CELL_SEPARATOR, cells);
        }
        return TEXT + "\n" + line.path("sentence").asString("");
    }

    /**
     * ai-service에 보낼 줄의 글. 표의 행은 빈 칸을 뺀 칸 글을 " | "로 잇는다. 안건 묶음 아래에서 고친 줄은
     * 이어받은 줄처럼 "{안건 제목}: "을 다시 붙인다
     */
    private static String sentence(JsonNode line, JsonNode source) {
        String heading = line.path("heading").asString("");
        if (!heading.isEmpty() && source != null && source.path("sentence").asString("").startsWith(heading + ": ")) {
            return heading + ": " + line.path("sentence").asString("");
        }
        if (TABLE.equals(line.path("type").asString(""))) {
            List<String> cells = new ArrayList<>();
            line.path("cells").forEach(cell -> {
                if (!cell.asString("").isBlank()) {
                    cells.add(cell.asString(""));
                }
            });
            return String.join(" | ", cells);
        }
        return line.path("sentence").asString("");
    }

    private static ArrayNode array(JsonNode node) {
        return node.isArray() ? (ArrayNode) node.deepCopy() : NODES.arrayNode();
    }
}
