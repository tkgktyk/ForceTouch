/*
 * Copyright 2015 Takagi Katsuyuki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.tkgktyk.xposed.forcetouchdetector.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.common.base.Strings;

import java.util.Collections;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import jp.tkgktyk.xposed.forcetouchdetector.FTD;
import jp.tkgktyk.xposed.forcetouchdetector.R;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.ActionInfo;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.ActionInfoList;

/**
 * Created by tkgktyk on 2015/07/02.
 */
public class FloatingActionActivity extends AppCompatActivity {

    private static final int REQUEST_ACTION = 1;

    @Bind(R.id.toolbar)
    Toolbar mToolbar;
    @Bind(R.id.recycler_view)
    RecyclerView mRecyclerView;

    private MyAdapter mMyAdapter;
    private ActionInfoList mActionList;
    private boolean mIsChanged;

    private FTD.Settings mSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_floating_action);
        ButterKnife.bind(this);

        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mActionList = ActionInfoList.fromPreference(
                FTD.getSharedPreferences(this)
                        .getString(getString(R.string.key_floating_action_list), ""));

        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mMyAdapter = new MyAdapter();
        mRecyclerView.setAdapter(mMyAdapter);
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.START | ItemTouchHelper.END) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder from,
                                  RecyclerView.ViewHolder to) {
                final int fromPos = from.getAdapterPosition();
                final int toPos = to.getAdapterPosition();
                Collections.swap(mActionList, fromPos, toPos);
                mIsChanged = true;
                mMyAdapter.notifyItemMoved(fromPos, toPos);
                return true;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int i) {
                final int pos = viewHolder.getAdapterPosition();
                mActionList.remove(pos);
                mIsChanged = true;
                mMyAdapter.notifyItemRemoved(pos);
            }
        });
        helper.attachToRecyclerView(mRecyclerView);

        mSettings = new FTD.Settings(this, FTD.getSharedPreferences(this));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_floating_action, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            case R.id.action_floating_action:
                onTestClicked(null);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    protected void onStop() {
        super.onStop();

        saveActionList();
    }

    private boolean saveActionList() {
        if (mIsChanged) {
            FTD.getSharedPreferences(this).edit()
                    .putString(getString(R.string.key_floating_action_list),
                            mActionList.toStringForPreference())
                    .apply();
            mIsChanged = false;
            MyApp.showToast(R.string.saved);
            return true;
        }
        return false;
    }

    @OnClick(R.id.add_fab)
    void onAddClicked(FloatingActionButton button) {
        Intent intent = new Intent(this, ActionPickerActivity.class);
        ActionPickerActivity.putExtras(intent, getSupportActionBar().getTitle(), false);
        startActivityForResult(intent, REQUEST_ACTION);
    }

    @OnClick(R.id.test_button)
    void onTestClicked(Button button) {
        boolean saved = saveActionList();
        if (mSettings.forceTouchEnable || mSettings.knuckleTouchEnable ||
                mSettings.wiggleTouchEnable) {
            if (saved) {
                mRecyclerView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        FloatingAction.show(FloatingActionActivity.this);
                    }
                }, 500);
            } else {
                FloatingAction.show(this);
            }
        } else {
            MyApp.showToast(R.string.note_floating_action);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ACTION:
                if (resultCode == RESULT_OK) {
                    MyApp.logD();
                    ActionInfo.Record record = (ActionInfo.Record) data
                            .getSerializableExtra(ActionPickerActivity.EXTRA_ACTION_RECORD);
                    ActionInfo actionInfo = new ActionInfo(record);
                    mActionList.add(actionInfo);
                    mIsChanged = true;
                    mMyAdapter.notifyItemInserted(mActionList.size() - 1);
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    protected static class Holder extends RecyclerView.ViewHolder {
        @Bind(R.id.icon)
        FloatingActionButton icon;
        @Bind(R.id.action_name)
        TextView name;
        @Bind(R.id.action_type)
        TextView type;

        public Holder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }

    private class MyAdapter extends RecyclerView.Adapter<Holder> {

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.view_action_list_item, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            ActionInfo info = mActionList.get(position);
            String name = info.getName();
            Bitmap icon = info.getIcon();
            if (Strings.isNullOrEmpty(name)) {
                switch (info.getType()) {
                    case ActionInfo.TYPE_NONE:
                        name = getString(R.string.none);
                        break;
                    default:
                        name = getString(R.string.not_found);
                }
            }
            holder.icon.setImageBitmap(icon);
            holder.icon.setBackgroundTintList(ColorStateList.valueOf(mSettings.floatingActionColor));
            holder.name.setText(name);
            switch (info.getType()) {
                case ActionInfo.TYPE_TOOL:
                    holder.type.setText(R.string.tools);
                    break;
                case ActionInfo.TYPE_APP:
                    holder.type.setText(R.string.apps);
                    break;
                case ActionInfo.TYPE_SHORTCUT:
                    holder.type.setText(R.string.shortcuts);
                    break;
                default:
                    holder.type.setText(null);
                    break;
            }
        }

        @Override
        public int getItemCount() {
            return mActionList.size();
        }
    }
}
