package jp.tkgktyk.xposed.forcetouchdetector.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.google.common.collect.Lists;

import java.util.ArrayList;

import butterknife.ButterKnife;
import butterknife.InjectView;
import jp.tkgktyk.xposed.forcetouchdetector.FTD;
import jp.tkgktyk.xposed.forcetouchdetector.R;

/**
 * Created by tkgktyk on 2015/06/07.
 */
public class ActionPickerActivity extends AppCompatActivity {
    private static final String EXTRA_TITLE = FTD.PREFIX_EXTRA + "TITLE";
    public static final String EXTRA_INTENT = FTD.PREFIX_EXTRA + "INTENT";

    public static void putExtras(Intent intent, CharSequence title) {
        intent.putExtra(EXTRA_TITLE, title);
    }

    @InjectView(R.id.toolbar)
    Toolbar mToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_picker);
        ButterKnife.inject(this);

        setSupportActionBar(mToolbar);
        getSupportActionBar().setTitle(getIntent().getStringExtra(EXTRA_TITLE));

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, new ActionPickerFragment())
                    .commit();
        }
    }

    public void returnActivity(Intent picked) {
        Intent intent = getIntent();
        intent.putExtra(EXTRA_INTENT, picked);
        setResult(RESULT_OK, intent);
        finish();
    }

    public void pickTool() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, new ToolPickerFragment())
                .addToBackStack(null)
                .commit();
    }

    public static class ActionPickerFragment extends ListFragment {
        private static final int REQUEST_PICK_TOOL = 1;
        private static final int REQUEST_PICK_APP = 2;
        private static final int REQUEST_PICK_SHORTCUT = 3;
        private static final int REQUEST_CREATE_SHORTCUT = 4;

        private static final int[] TITLE_ID_LIST = {
                R.string.tools,
                R.string.apps,
                R.string.shortcuts,
                R.string.none
        };

        public ActionPickerFragment() {
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            ArrayList<String> titles = Lists.newArrayList();
            for (int id : TITLE_ID_LIST) {
                titles.add(getString(id));
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(view.getContext(),
                    android.R.layout.simple_list_item_1, titles);
            setListAdapter(adapter);
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            ActionPickerActivity activity = (ActionPickerActivity) getActivity();
            switch (TITLE_ID_LIST[position]) {
                case R.string.tools: {
                    activity.pickTool();
                    break;
                }
                case R.string.apps: {
                    Intent intent = new Intent(Intent.ACTION_PICK_ACTIVITY);
                    intent.putExtra(Intent.EXTRA_INTENT, new Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_LAUNCHER));
                    intent.putExtra(Intent.EXTRA_TITLE, activity.getTitle());

                    startActivityForResult(intent, REQUEST_PICK_APP);
                    break;
                }
                case R.string.shortcuts: {
                    Intent intent = new Intent(Intent.ACTION_PICK_ACTIVITY);
                    intent.putExtra(Intent.EXTRA_INTENT, new Intent(
                            Intent.ACTION_CREATE_SHORTCUT));
                    intent.putExtra(Intent.EXTRA_TITLE, activity.getTitle());

                    startActivityForResult(intent, REQUEST_PICK_SHORTCUT);
                    break;
                }
                case R.string.none: {
                    activity.returnActivity(new Intent());
                    activity.finish();
                    break;
                }
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            ActionPickerActivity activity = (ActionPickerActivity) getActivity();
            switch (requestCode) {
                case REQUEST_PICK_SHORTCUT:
                    if (RESULT_OK == resultCode) {
                        // expand shortcut to create if need
                        startActivityForResult(data, REQUEST_CREATE_SHORTCUT);
                    }
                    break;
                case REQUEST_CREATE_SHORTCUT:
                    if (RESULT_OK == resultCode) {
                        activity.returnActivity((Intent) data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT));
                    }
                    break;
                case REQUEST_PICK_APP:
                    if (RESULT_OK == resultCode) {
                        activity.returnActivity(data);
                    }
                    break;
                case REQUEST_PICK_TOOL:
                    if (RESULT_OK == resultCode) {
                        activity.returnActivity(data);
                    }
                    break;
                default:
                    super.onActivityResult(requestCode, resultCode, data);
            }
        }
    }

    public static class ToolPickerFragment extends ListFragment {
        private static final String[] ACTION_LIST = {
                FTD.ACTION_BACK,
                FTD.ACTION_HOME,
                FTD.ACTION_RECENTS,
                FTD.ACTION_EXPAND_NOTIFICATIONS,
                FTD.ACTION_EXPAND_QUICK_SETTINGS,
                FTD.ACTION_DOUBLE_TAP,
                FTD.ACTION_LONG_PRESS,
                FTD.ACTION_LONG_PRESS_FULL,
        };

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            Context context = view.getContext();

            ArrayList<String> titles = Lists.newArrayList();
            for (String action : ACTION_LIST) {
                titles.add(FTD.getActionName(context, action));
            }
            ArrayAdapter adapter = new ArrayAdapter<>(context,
                    android.R.layout.simple_list_item_1, titles);
            setListAdapter(adapter);
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            ActionPickerActivity activity = (ActionPickerActivity) getActivity();
            activity.returnActivity(new Intent(ACTION_LIST[position]));
        }
    }
}
