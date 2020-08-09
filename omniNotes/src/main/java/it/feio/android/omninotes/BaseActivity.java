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

import static it.feio.android.omninotes.utils.Constants.PREFS_NAME;
import static it.feio.android.omninotes.utils.ConstantsBase.INTENT_UPDATE_DASHCLOCK;
import static it.feio.android.omninotes.utils.ConstantsBase.PREF_NAVIGATION;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.ViewConfiguration;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import it.feio.android.omninotes.helpers.LanguageHelper;
import it.feio.android.omninotes.helpers.LogDelegate;
import it.feio.android.omninotes.models.Note;
import it.feio.android.omninotes.models.PasswordValidator;
import it.feio.android.omninotes.utils.Navigation;
import it.feio.android.omninotes.utils.PasswordHelper;
import it.feio.android.omninotes.widget.ListWidgetProvider;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

//BaseActivity는 코드구현을 한 뒤 다른곳에서 사용하는 용도로 쓴다
//보통의 액티비티에서는 AppCompatActivity를 상속받지만 BaseActivity를 대신 상속받아서 사용한다
@SuppressLint("Registered")
public class BaseActivity extends AppCompatActivity {

  protected static final int TRANSITION_VERTICAL = 0;
  protected static final int TRANSITION_HORIZONTAL = 1;

  //SharedPreferences 간단한 데이터 저장
  protected SharedPreferences prefs;

  protected String navigation;
  protected String navigationTmp; // used for widget navigation


  //툴바의 메뉴 생성
  @Override
  public boolean onCreateOptionsMenu (Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.menu_list, menu);
    return super.onCreateOptionsMenu(menu); //Menu Inflater를 통하여 XML Menu 리소스에 정의된 내용을 파싱 하여 Menu 객체를 생성하고 추가
  }

  //앱 구동 시작 시에 필요
  @Override
  protected void attachBaseContext (Context newBase) {
    Context context = LanguageHelper.updateLanguage(newBase, null);
    super.attachBaseContext(context);
  }

  //자세한 기능 모르겠음
  @Override
  protected void onCreate (Bundle savedInstanceState) {
    prefs = getSharedPreferences(PREFS_NAME, MODE_MULTI_PROCESS);
    // Forces menu overflow icon
    try {
      ViewConfiguration config = ViewConfiguration.get(this.getApplicationContext());
      Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
      if (menuKeyField != null) {
        menuKeyField.setAccessible(true);
        menuKeyField.setBoolean(config, false);
      }
    } catch (Exception e) {
      LogDelegate.w("Just a little issue in physical menu button management", e);
    }
    super.onCreate(savedInstanceState);
  }


  @Override
  protected void onResume () {
    super.onResume();
    //네비게이션 리스트 가져온다(툴바의 좌측 햄버거메뉴, Notes, Trash, Settings 등)
    String navNotes = getResources().getStringArray(R.array.navigation_list_codes)[0];
    navigation = prefs.getString(PREF_NAVIGATION, navNotes);
    LogDelegate.d(prefs.getAll().toString());
  }


  //토스트 메세지 생성용 메소드
  protected void showToast (CharSequence text, int duration) {
    if (prefs.getBoolean("settings_enable_info", true)) {
      Toast.makeText(getApplicationContext(), text, duration).show();
    }
  }


  /**
   * Method to validate security password to protect a list of notes. When "Request password on access" in switched on
   * this check not required all the times. It uses an interface callback.
   */
  //메모 목록을 보호하기 위해 보안 암호를 확인하는 방법입니다. "액세스시 암호 요청"이 켜져있을 때
  //   *이 확인은 항상 필요하지 않습니다. 인터페이스 콜백을 사용합니다.

  //암호 요청, onPasswordValidated-비밀번호 유효성 체크
  public void requestPassword (final Activity mActivity, List<Note> notes,
      final PasswordValidator mPasswordValidator) {
    if (prefs.getBoolean("settings_password_access", false)) {
      mPasswordValidator.onPasswordValidated(PasswordValidator.Result.SUCCEED);
      return;
    }

    boolean askForPassword = false;
    for (Note note : notes) {
      if (note.isLocked()) {
        askForPassword = true;
        break;
      }
    }
    if (askForPassword) {
      PasswordHelper.requestPassword(mActivity, mPasswordValidator);
    } else {
      mPasswordValidator.onPasswordValidated(PasswordValidator.Result.SUCCEED);
    }
  }


  //햄버거 메뉴 네비게이션 수정
  public boolean updateNavigation (String nav) {
    if (nav.equals(navigationTmp) || (navigationTmp == null && Navigation.getNavigationText().equals(nav))) {
      return false;
    }
    prefs.edit().putString(PREF_NAVIGATION, nav).apply();
    navigation = nav;
    navigationTmp = null;
    return true;
  }


  /**
   * Retrieves resource by name
   *
   * @returnnotifyAppWidgets
   */
  //이름으로 리소스를 검색합니다

  private String getStringResourceByName (String aString) {
    String packageName = getApplicationContext().getPackageName();
    int resId = getResources().getIdentifier(aString, "string", packageName);
    return getString(resId);
  }


  /**
   * Notifies App Widgets about data changes so they can update theirselves
   */
  //데이터 변경에 대해 앱 위젯에 알림을 보내서 스스로 업데이트 할 수 있습니다.
  //위젯에 (변경사항)알리기
  public static void notifyAppWidgets (Context context) {
    // Home widgets
    AppWidgetManager mgr = AppWidgetManager.getInstance(context);
    int[] ids = mgr.getAppWidgetIds(new ComponentName(context, ListWidgetProvider.class));
    LogDelegate.d("Notifies AppWidget data changed for widgets " + Arrays.toString(ids));
    mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list);

    // Dashclock
    LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(INTENT_UPDATE_DASHCLOCK));
  }


  //애니메이션 전환
  //direction-방향, fade in, fade out - 서서히 사라지거나 서서히 나타나는 애니메이션(효과)
  @SuppressLint("InlinedApi")
  protected void animateTransition (FragmentTransaction transaction, int direction) {
    if (direction == TRANSITION_HORIZONTAL) {
      transaction.setCustomAnimations(R.anim.fade_in_support, R.anim.fade_out_support,
          R.anim.fade_in_support, R.anim.fade_out_support);
    }
    if (direction == TRANSITION_VERTICAL) {
      transaction.setCustomAnimations(
          R.anim.anim_in, R.anim.anim_out, R.anim.anim_in_pop, R.anim.anim_out_pop);
    }
  }


  //액션바(툴바) 타이틀 이름 설정
  protected void setActionBarTitle (String title) {
    // Creating a spannable to support custom fonts on ActionBar
    int actionBarTitle = Resources.getSystem().getIdentifier("action_bar_title", "ID", "android");
    android.widget.TextView actionBarTitleView = getWindow().findViewById(actionBarTitle);
    Typeface font = Typeface.createFromAsset(getAssets(), "fonts/Roboto-Regular.ttf");
    if (actionBarTitleView != null) {
      actionBarTitleView.setTypeface(font);
    }

    if (getSupportActionBar() != null) {
      getSupportActionBar().setTitle(title);
    }
  }


  public String getNavigationTmp () {
    return navigationTmp;
  }


  //onKeyDown-키보드가 눌렸을 때, KEYCODE_MENU-사용자 단말기의 메뉴버튼 클릭시
  @Override
  public boolean onKeyDown (int keyCode, KeyEvent event) {
    return keyCode == KeyEvent.KEYCODE_MENU || super.onKeyDown(keyCode, event);
  }
}
