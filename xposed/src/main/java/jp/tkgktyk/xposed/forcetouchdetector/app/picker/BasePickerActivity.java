package jp.tkgktyk.xposed.forcetouchdetector.app.picker;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Window;
import android.widget.ListView;

import butterknife.ButterKnife;
import butterknife.InjectView;
import jp.tkgktyk.xposed.forcetouchdetector.FTD;
import jp.tkgktyk.xposed.forcetouchdetector.R;

/**
 * Created by tkgktyk on 2015/06/07.
 */
public class BasePickerActivity extends AppCompatActivity {
    private static final String EXTRA_TITLE = FTD.PREFIX_EXTRA + "title";
    public static final String EXTRA_KEY = FTD.PREFIX_EXTRA + "key";
    public static final String EXTRA_INTENT = FTD.PREFIX_EXTRA + "intent";

    public static void putExtras(Intent intent, CharSequence title, String key) {
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_KEY, key);
    }

    @InjectView(R.id.toolbar)
    Toolbar mToolbar;
    @InjectView(R.id.list_view)
    ListView mListView;

    protected String mKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_picker);
        ButterKnife.inject(this);

        setSupportActionBar(mToolbar);

        setTitle(getIntent().getStringExtra(EXTRA_TITLE));
        mKey = getIntent().getStringExtra(EXTRA_KEY);
    }

    protected void returnActivity(Intent picked) {
        Intent intent = getIntent();
        intent.putExtra(EXTRA_INTENT, picked);
        setResult(RESULT_OK, intent);
        finish();
    }
}
