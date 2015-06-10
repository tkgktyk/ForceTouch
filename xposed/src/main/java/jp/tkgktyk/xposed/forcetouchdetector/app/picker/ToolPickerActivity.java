package jp.tkgktyk.xposed.forcetouchdetector.app.picker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import com.google.common.collect.Lists;

import java.util.ArrayList;

import jp.tkgktyk.xposed.forcetouchdetector.FTD;

/**
 * Created by tkgktyk on 2015/06/07.
 */
public class ToolPickerActivity extends BasePickerActivity {

    public static final String[] ACTION_LIST = {
            FTD.ACTION_BACK,
            FTD.ACTION_HOME,
            FTD.ACTION_RECENTS,
            FTD.ACTION_EXPAND_NOTIFICATIONS,
            FTD.ACTION_EXPAND_QUICK_SETTINGS,
            FTD.ACTION_DOUBLE_TAP,
            FTD.ACTION_LONG_PRESS,
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ArrayList<String> titles = Lists.newArrayList();
        for (String action : ACTION_LIST) {
            titles.add(FTD.getActionName(this, action));
        }
        ArrayAdapter adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, titles);
        mListView.setAdapter(adapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                setResult(RESULT_OK, new Intent(ACTION_LIST[position]));
                finish();
            }
        });
    }
}
