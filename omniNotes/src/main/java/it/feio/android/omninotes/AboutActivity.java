/*
 * Copyright (C) 2013-2020 Federico Iosue (federico@iosue.it)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.feio.android.omninotes;

import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;
import android.webkit.WebView;


//앱의 settings-about-Info 클릭 시
public class AboutActivity extends BaseActivity {

  protected void onCreate (Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_about);

    WebView webview = findViewById(R.id.webview);
    webview.loadUrl("file:///android_asset/html/about.html");

    initUI();
  }


  //start 사이클 시 애널리틱스(구글 애널리틱스)(방분자 데이터 수집) trackScreenView 시작
  @Override
  public void onStart () {
    ((OmniNotes) getApplication()).getAnalyticsHelper().trackScreenView(getClass().getName());
    super.onStart();
  }


  @Override
  public boolean onNavigateUp () {
    onBackPressed();
    return true;
  }


  //init-초기화, initUI UI 초기화
  //툴바 생성하고 클릭리스너 붙이는 과정일듯
  private void initUI () {
    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setHomeButtonEnabled(true);
    toolbar.setNavigationOnClickListener(v -> onNavigateUp());
  }

}
