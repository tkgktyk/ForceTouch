package jp.tkgktyk.xposed.forcetouchdetector.app.picker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import com.google.common.collect.Lists;

import java.util.ArrayList;

import jp.tkgktyk.xposed.forcetouchdetector.R;

/**
 * Created by tkgktyk on 2015/06/07.
 */
public class ActionPickerActivity extends BasePickerActivity {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        ArrayList<String> titles = Lists.newArrayList();
        for (int id : TITLE_ID_LIST) {
            titles.add(getString(id));
        }
        ArrayAdapter adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, titles);
        mListView.setAdapter(adapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (TITLE_ID_LIST[position]) {
                    case R.string.tools: {
                        Intent intent = new Intent(view.getContext(), ToolPickerActivity.class);
                        putExtras(intent, getTitle(), mKey);
                        startActivityForResult(intent, REQUEST_PICK_TOOL);
                        break;
                    }
                    case R.string.apps: {
                        Intent intent = new Intent(Intent.ACTION_PICK_ACTIVITY);
                        intent.putExtra(Intent.EXTRA_INTENT, new Intent(Intent.ACTION_MAIN)
                                .addCategory(Intent.CATEGORY_LAUNCHER));
                        intent.putExtra(Intent.EXTRA_TITLE, getTitle());

                        startActivityForResult(intent, REQUEST_PICK_APP);
                        break;
                    }
                    case R.string.shortcuts: {
                        Intent intent = new Intent(Intent.ACTION_PICK_ACTIVITY);
                        intent.putExtra(Intent.EXTRA_INTENT, new Intent(
                                Intent.ACTION_CREATE_SHORTCUT));
                        intent.putExtra(Intent.EXTRA_TITLE, getTitle());

                        startActivityForResult(intent, REQUEST_PICK_SHORTCUT);
                        break;
                    }
                    case R.string.none: {
                        returnActivity(new Intent());
                        finish();
                        break;
                    }
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_PICK_SHORTCUT:
                if (RESULT_OK == resultCode) {
                    // expand shortcut to create if need
                    startActivityForResult(data, REQUEST_CREATE_SHORTCUT);
                }
                break;
            case REQUEST_CREATE_SHORTCUT:
                if (RESULT_OK == resultCode) {
                    returnActivity((Intent) data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT));
                }
                break;
            case REQUEST_PICK_APP:
                if (RESULT_OK == resultCode) {
                    returnActivity(data);
                }
                break;
            case REQUEST_PICK_TOOL:
                if (RESULT_OK == resultCode) {
                    returnActivity(data);
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

}
