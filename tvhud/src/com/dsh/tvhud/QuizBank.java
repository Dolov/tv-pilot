package com.dsh.tvhud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 题库：只做解析/抽题/判分，不碰 Android 资源（读取在 PetStore.loadQuiz）。
 *
 * JSON 用「数组的数组」而不是对象，是为了让 tests/java 的迷你 JSON 桩也能离线解析：
 * {"v":1,"items":[[id,theme,level,prompt,voice,[opt...],answer], ...]}
 */
public final class QuizBank {
    public static final int PASS_CORRECT = 2;

    public static final class Question {
        public final String id;
        public final String theme;
        public final int level;
        public final String prompt;
        public final String voice;
        public final String[] options;
        public final int answer;

        Question(String id, String theme, int level, String prompt, String voice,
                 String[] options, int answer) {
            this.id = id;
            this.theme = theme;
            this.level = level;
            this.prompt = prompt;
            this.voice = voice;
            this.options = options;
            this.answer = answer;
        }

        public boolean isCorrect(int selected) {
            return selected == answer;
        }
    }

    private final List<Question> items = new ArrayList<Question>();
    private final Random random = new Random();

    public static QuizBank parse(String json) {
        QuizBank bank = new QuizBank();
        if (json == null || json.isEmpty()) {
            return bank;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("items");
            if (arr == null) {
                return bank;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONArray row = arr.getJSONArray(i);
                if (row == null || row.length() < 7) {
                    continue;
                }
                String id = row.getString(0);
                String theme = row.getString(1);
                int level = row.getInt(2);
                String prompt = row.getString(3);
                String voice = row.getString(4);
                JSONArray opt = row.getJSONArray(5);
                if (opt == null || opt.length() < 3) {
                    continue;
                }
                String[] options = new String[opt.length()];
                for (int k = 0; k < opt.length(); k++) {
                    options[k] = opt.getString(k);
                }
                int answer = row.getInt(6);
                if (answer < 0 || answer >= options.length || prompt.isEmpty()) {
                    continue;
                }
                bank.items.add(new Question(id, theme, level, prompt, voice, options, answer));
            }
        } catch (Throwable ignored) {
        }
        return bank;
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public List<Question> all() {
        return items;
    }

    /** 抽 n 道不重复的题；题库不足时有多少给多少。 */
    public List<Question> pick(int n) {
        List<Question> copy = new ArrayList<Question>(items);
        Collections.shuffle(copy, random);
        int count = Math.min(n, copy.size());
        return new ArrayList<Question>(copy.subList(0, count));
    }

    /** 抽 n 道，尽量避开最近出现过的 id。 */
    public List<Question> pick(int n, HashSet<String> recent) {
        List<Question> preferred = new ArrayList<Question>();
        List<Question> fallback = new ArrayList<Question>();
        for (Question q : items) {
            if (recent != null && recent.contains(q.id)) {
                fallback.add(q);
            } else {
                preferred.add(q);
            }
        }
        Collections.shuffle(preferred, random);
        Collections.shuffle(fallback, random);
        List<Question> out = new ArrayList<Question>();
        for (Question q : preferred) {
            if (out.size() >= n) {
                break;
            }
            out.add(q);
        }
        for (Question q : fallback) {
            if (out.size() >= n) {
                break;
            }
            out.add(q);
        }
        return out;
    }
}
