package dev.linjian.peek;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Local review card UI for Sigillo receipts. No network calls are made here.
 */
public class SigilloActivity extends Activity {
    private final List<RatingBar> itemRatings = new ArrayList<>();
    private final List<EditText> itemNotes = new ArrayList<>();
    private RatingBar foreplayRating;
    private RatingBar processRating;
    private RatingBar aftercareRating;
    private EditText overallNote;
    private String reviewId = "";
    private JSONObject review;

    private int dp(float value) {
        return Math.max(1, Math.round(value * getResources().getDisplayMetrics().density));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        reviewId = getIntent() == null ? "" : getIntent().getStringExtra("review_id");
        if (reviewId == null) reviewId = "";
        JSONObject got = SigilloState.get(this, reviewId);
        review = got.optJSONObject("review");
        if (review == null) {
            finish();
            return;
        }
        render();
    }

    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        reviewId = intent == null ? "" : intent.getStringExtra("review_id");
        if (reviewId == null) reviewId = "";
        JSONObject got = SigilloState.get(this, reviewId);
        review = got.optJSONObject("review");
        if (review != null) render();
    }

    private void render() {
        boolean sealed = "submitted".equals(review.optString("status"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(246, 247, 243));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(26), dp(20), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView kicker = text("SIGILLO · " + (sealed ? "SEALED" : "OPEN"), 12, Color.rgb(83, 111, 88));
        kicker.setLetterSpacing(0.12f);
        root.addView(kicker);

        TextView title = text(sealed ? "这张回执已经封缄" : "给这一场留一张回执", 26, Color.rgb(31, 37, 33));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = lp();
        titleLp.topMargin = dp(8);
        root.addView(title, titleLp);

        String context = review.optString("context", "").trim();
        if (!context.isEmpty()) {
            TextView sub = text(context, 14, Color.rgb(92, 98, 94));
            LinearLayout.LayoutParams subLp = lp();
            subLp.topMargin = dp(8);
            root.addView(sub, subLp);
        }

        TextView rule = text(sealed
                ? "封缄后的内容只读。星数是当时的体验记录，不会自动变成“下次照做”的命令。"
                : "逐项打星，想说什么就写什么。封缄后不可修改；高分也不会变成机械复读清单。",
                13, Color.rgb(108, 112, 108));
        LinearLayout.LayoutParams ruleLp = lp();
        ruleLp.topMargin = dp(10);
        ruleLp.bottomMargin = dp(18);
        root.addView(rule, ruleLp);

        itemRatings.clear();
        itemNotes.clear();

        addSectionTitle(root, "三项总评");
        JSONObject fixed = review.optJSONObject("fixed");
        foreplayRating = addFixed(root, "前戏", fixedStar(fixed, "foreplay"), sealed);
        processRating = addFixed(root, "过程", fixedStar(fixed, "process"), sealed);
        aftercareRating = addFixed(root, "事后", fixedStar(fixed, "aftercare"), sealed);

        addSectionTitle(root, "这一场的细节");
        JSONArray items = review.optJSONArray("items");
        if (items == null) items = new JSONArray();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            addItem(root, item, i + 1, sealed);
        }

        addSectionTitle(root, "改进与建议");
        overallNote = new EditText(this);
        overallNote.setText(review.optString("note", ""));
        overallNote.setTextSize(15);
        overallNote.setTextColor(Color.rgb(36, 40, 37));
        overallNote.setHint(sealed ? "" : "没话说可以空着。");
        overallNote.setHintTextColor(Color.rgb(150, 154, 151));
        overallNote.setGravity(Gravity.TOP);
        overallNote.setMinLines(3);
        overallNote.setPadding(dp(14), dp(12), dp(14), dp(12));
        overallNote.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        overallNote.setBackground(cardBackground());
        overallNote.setEnabled(!sealed);
        LinearLayout.LayoutParams noteLp = lp();
        noteLp.topMargin = dp(8);
        root.addView(overallNote, noteLp);

        if (sealed) {
            String agentNote = review.optString("agent_note", "").trim();
            if (!agentNote.isEmpty()) {
                addSectionTitle(root, "玄砚留给下次自己的话");
                TextView an = text("“" + agentNote + "”", 15, Color.rgb(44, 62, 49));
                an.setPadding(dp(14), dp(12), dp(14), dp(12));
                an.setBackground(cardBackground());
                root.addView(an, lp());
            }
        } else {
            Button seal = new Button(this);
            seal.setText("封缄");
            seal.setTextSize(16);
            seal.setAllCaps(false);
            seal.setTextColor(Color.WHITE);
            GradientDrawable sealBg = new GradientDrawable();
            sealBg.setColor(Color.rgb(61, 105, 72));
            sealBg.setCornerRadius(dp(18));
            seal.setBackground(sealBg);
            seal.setOnClickListener(v -> sealNow());
            LinearLayout.LayoutParams sealLp = lp();
            sealLp.topMargin = dp(22);
            sealLp.height = dp(52);
            root.addView(seal, sealLp);

            TextView irrev = text("封缄是一锤定音：提交后这张单不能再改。", 12, Color.rgb(120, 124, 121));
            irrev.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams irrLp = lp();
            irrLp.topMargin = dp(9);
            root.addView(irrev, irrLp);
        }

        setContentView(scroll);
    }

    private RatingBar addFixed(LinearLayout root, String label, float rating, boolean sealed) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.setBackground(cardBackground());

        TextView name = text(label, 15, Color.rgb(46, 52, 48));
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(name, nameLp);

        RatingBar bar = ratingBar(rating, sealed);
        row.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams rowLp = lp();
        rowLp.topMargin = dp(7);
        root.addView(row, rowLp);
        return bar;
    }

    private void addItem(LinearLayout root, JSONObject item, int index, boolean sealed) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(cardBackground());

        TextView meta = text(String.format(Locale.CHINA, "%02d  %s · %s", index,
                item.optString("dim"), item.optString("tag")), 12, Color.rgb(86, 111, 90));
        card.addView(meta, lp());

        TextView label = text(item.optString("label"), 16, Color.rgb(35, 40, 36));
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams labelLp = lp();
        labelLp.topMargin = dp(5);
        card.addView(label, labelLp);

        float star = 0f;
        Object raw = item.opt("star");
        if (raw instanceof Number) star = ((Number) raw).floatValue();
        RatingBar rating = ratingBar(star, sealed);
        LinearLayout.LayoutParams barLp = lp();
        barLp.topMargin = dp(7);
        card.addView(rating, barLp);
        itemRatings.add(rating);

        EditText note = new EditText(this);
        note.setText(item.optString("note", ""));
        note.setHint(sealed ? "" : "备注（可空）");
        note.setTextSize(14);
        note.setTextColor(Color.rgb(49, 54, 50));
        note.setHintTextColor(Color.rgb(151, 155, 152));
        note.setSingleLine(false);
        note.setMaxLines(4);
        note.setEnabled(!sealed);
        note.setPadding(0, dp(5), 0, 0);
        note.setBackgroundColor(Color.TRANSPARENT);
        card.addView(note, lp());
        itemNotes.add(note);

        LinearLayout.LayoutParams cardLp = lp();
        cardLp.topMargin = dp(9);
        root.addView(card, cardLp);
    }

    private RatingBar ratingBar(float value, boolean sealed) {
        RatingBar bar = new RatingBar(this, null, android.R.attr.ratingBarStyleSmall);
        bar.setNumStars(5);
        bar.setStepSize(0.5f);
        bar.setRating(value);
        bar.setIsIndicator(sealed);
        return bar;
    }

    private void sealNow() {
        JSONArray stars = new JSONArray();
        JSONArray notes = new JSONArray();
        for (int i = 0; i < itemRatings.size(); i++) {
            float star = itemRatings.get(i).getRating();
            if (star < 1f) {
                Toast.makeText(this, "第 " + (i + 1) + " 条还没打星。", Toast.LENGTH_SHORT).show();
                return;
            }
            stars.put((double) star);
            notes.put(itemNotes.get(i).getText().toString());
        }

        JSONObject fixed = new JSONObject();
        try {
            fixed.put("foreplay", Math.round(foreplayRating.getRating() * 20f));
            fixed.put("process", Math.round(processRating.getRating() * 20f));
            fixed.put("aftercare", Math.round(aftercareRating.getRating() * 20f));
        } catch (Exception ignored) { }

        JSONObject result = SigilloState.submitReview(this, reviewId, fixed, stars, notes,
                overallNote == null ? "" : overallNote.getText().toString());
        if (!result.optBoolean("ok")) {
            Toast.makeText(this, result.optString("message", "封缄失败"), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "已封缄。玄砚下次读回执时会拿到原话。", Toast.LENGTH_LONG).show();
        finish();
    }

    private float fixedStar(JSONObject fixed, String key) {
        if (fixed == null) return 0f;
        return Math.max(0f, Math.min(5f, fixed.optInt(key, 0) / 20f));
    }

    private void addSectionTitle(LinearLayout root, String title) {
        TextView t = text(title, 14, Color.rgb(69, 77, 71));
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams p = lp();
        p.topMargin = dp(18);
        p.bottomMargin = dp(3);
        root.addView(t, p);
    }

    private TextView text(String value, float sp, int color) {
        TextView v = new TextView(this);
        v.setText(value == null ? "" : value);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setLineSpacing(dp(2), 1f);
        return v;
    }

    private LinearLayout.LayoutParams lp() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private GradientDrawable cardBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(15));
        bg.setStroke(dp(1), Color.rgb(226, 230, 225));
        return bg;
    }
}
