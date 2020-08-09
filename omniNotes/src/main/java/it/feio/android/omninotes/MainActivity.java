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

import static it.feio.android.omninotes.utils.ConstantsBase.ACTION_NOTIFICATION_CLICK;
import static it.feio.android.omninotes.utils.ConstantsBase.ACTION_RESTART_APP;
import static it.feio.android.omninotes.utils.ConstantsBase.ACTION_SEND_AND_EXIT;
import static it.feio.android.omninotes.utils.ConstantsBase.ACTION_SHORTCUT;
import static it.feio.android.omninotes.utils.ConstantsBase.ACTION_SHORTCUT_WIDGET;
import static it.feio.android.omninotes.utils.ConstantsBase.ACTION_START_APP;
import static it.feio.android.omninotes.utils.ConstantsBase.ACTION_WIDGET;
import static it.feio.android.omninotes.utils.ConstantsBase.ACTION_WIDGET_TAKE_PHOTO;
import static it.feio.android.omninotes.utils.ConstantsBase.INTENT_GOOGLE_NOW;
import static it.feio.android.omninotes.utils.ConstantsBase.INTENT_KEY;
import static it.feio.android.omninotes.utils.ConstantsBase.INTENT_NOTE;
import static it.feio.android.omninotes.utils.ConstantsBase.PREF_PASSWORD;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.drawerlayout.widget.DrawerLayout;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentTransaction;
import butterknife.BindView;
import butterknife.ButterKnife;
import de.greenrobot.event.EventBus;
import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;
import it.feio.android.omninotes.async.UpdateWidgetsTask;
import it.feio.android.omninotes.async.bus.PasswordRemovedEvent;
import it.feio.android.omninotes.async.bus.SwitchFragmentEvent;
import it.feio.android.omninotes.async.notes.NoteProcessorDelete;
import it.feio.android.omninotes.db.DbHelper;
import it.feio.android.omninotes.helpers.LogDelegate;
import it.feio.android.omninotes.helpers.NotesHelper;
import it.feio.android.omninotes.intro.IntroActivity;
import it.feio.android.omninotes.models.Attachment;
import it.feio.android.omninotes.models.Category;
import it.feio.android.omninotes.models.Note;
import it.feio.android.omninotes.models.ONStyle;
import it.feio.android.omninotes.utils.FileProviderHelper;
import it.feio.android.omninotes.utils.PasswordHelper;
import it.feio.android.omninotes.utils.SystemHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

//베이스 액티비티 상속
public class MainActivity extends BaseActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

  private static boolean isPasswordAccepted = false;
  public final static String FRAGMENT_DRAWER_TAG = "fragment_drawer";
  public final static String FRAGMENT_LIST_TAG = "fragment_list";
  public final static String FRAGMENT_DETAIL_TAG = "fragment_detail";
  public final static String FRAGMENT_SKETCH_TAG = "fragment_sketch";
  public Uri sketchUri;
  //레이아웃 연결
  @BindView(R.id.crouton_handle)
  ViewGroup croutonViewContainer;
  @BindView(R.id.toolbar)
  Toolbar toolbar; //앱 상단의 바(구 버전에서는 액션바였던것)
  @BindView(R.id.drawer_layout)
  DrawerLayout drawerLayout;
  boolean prefsChanged = false;
  private FragmentManager mFragmentManager;

  @Override
  protected void onCreate (Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    //옴니노트 테마 스타일 세팅
    setTheme(R.style.OmniNotesTheme_ApiSpec);
    setContentView(R.layout.activity_main);
    //버터나이프 라이브러리 사용(뷰 선언 및 연결 작업 도와주는 라이브러리)
    ButterKnife.bind(this);
    //이벤트버스(Activity, Fragment, Service, Thread 에 데이터 전달용으로 쉽게 쓸 수 있는 플러그인)
    EventBus.getDefault().register(this); //이벤트버스 장착(이벤트 수신받을 수 있게)
    prefs.registerOnSharedPreferenceChangeListener(this);

    initUI();

    //인트로 액티비티 실행?
    if (IntroActivity.mustRun()) {
      startActivity(new Intent(getApplicationContext(), IntroActivity.class));
    }

//		new UpdaterTask(this).execute(); Removed due to missing backend
  }


  @Override
  protected void onResume () { //앱이 다시 실행 될 때
    super.onResume();
    if (isPasswordAccepted) { //비밀번호가 맞으면 init()메소드 실행
      init();
    } else {
      checkPassword();
    }
  }


  @Override
  protected void onStop () { //앱이 멈췄을 때
    super.onStop(); //부모클래스(BaseActivity)의 onStop()메소드 실행
    EventBus.getDefault().unregister(this); //이벤트버스 해제
    //이벤트버스 - 이벤트를 수신 받기 원하는 곳을 eventbus에 register해두면 이벤트를 수신 받을 수 있다
  }


  private void initUI () {
    setSupportActionBar(toolbar); //액션바를 툴바로 설정하여 세팅(액션바를 툴바로 대체)
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setHomeButtonEnabled(true);
  }


  /**
   * This method starts the bootstrap chain.
   */
  //비밀번호 체크, 맞으면 init()
  private void checkPassword () {
    if (prefs.getString(PREF_PASSWORD, null) != null
        && prefs.getBoolean("settings_password_access", false)) {
      PasswordHelper.requestPassword(this, passwordConfirmed -> { //유틸폴더의 PasswordHelper클래스 사용
        switch (passwordConfirmed) {
          case SUCCEED:
            init();
            break;
          case FAIL:
            finish();
            break;
          case RESTORE:
            PasswordHelper.resetPassword(this);
        }
      });
    } else {
      init();
    }
  }


  // 비밀번호 삭제 이벤트
  public void onEvent (PasswordRemovedEvent passwordRemovedEvent) {
    showMessage(R.string.password_successfully_removed, ONStyle.ALERT);
    init();
  }


  // 로그인 완료 후 초기화, init - 초기화
  private void init () {
    isPasswordAccepted = true;

    getFragmentManagerInstance(); //액티비티에서 프래그먼트를 다루기 위해

    //NavigationDrawer - 왼쪽에 슬라이드 형식으로 튀어나오는 패널
    NavigationDrawerFragment mNavigationDrawerFragment = (NavigationDrawerFragment) getFragmentManagerInstance()
        .findFragmentById(R.id.navigation_drawer); //NavigationDrawer 연결
    if (mNavigationDrawerFragment == null) {
      FragmentTransaction fragmentTransaction = getFragmentManagerInstance().beginTransaction();
      fragmentTransaction.replace(R.id.navigation_drawer, new NavigationDrawerFragment(),
          FRAGMENT_DRAWER_TAG).commit();
    }

    if (getFragmentManagerInstance().findFragmentByTag(FRAGMENT_LIST_TAG) == null) {
      FragmentTransaction fragmentTransaction = getFragmentManagerInstance().beginTransaction();
      fragmentTransaction.add(R.id.fragment_container, new ListFragment(), FRAGMENT_LIST_TAG).commit();
    }

    handleIntents(); //handleIntents 메소드 실행
  }

  //액티비티에서 프래그먼트 사용 위한 것으로 추정
  private FragmentManager getFragmentManagerInstance () {
    if (mFragmentManager == null) {
      mFragmentManager = getSupportFragmentManager();
    }
    return mFragmentManager;
  }

  //현재 화면에서 다시 현재 화면 호출 시
  @Override
  protected void onNewIntent (Intent intent) {
    if (intent.getAction() == null) {
      intent.setAction(ACTION_START_APP);
    }
    super.onNewIntent(intent);
    setIntent(intent);
    handleIntents();
    LogDelegate.d("onNewIntent");
  }


  public MenuItem getSearchMenuItem () {
    Fragment f = checkFragmentInstance(R.id.fragment_container, ListFragment.class);
    if (f != null) {
      return ((ListFragment) f).getSearchMenuItem();
    } else {
      return null;
    }
  }


  //태그 수정 - editCategory 호출
  public void editTag (Category tag) {
    Fragment f = checkFragmentInstance(R.id.fragment_container, ListFragment.class);
    if (f != null) {
      ((ListFragment) f).editCategory(tag);
    }
  }


  //ListFragment 클래스의 메소드 호출 - toggleSearchLabel, initNotesList
  public void initNotesList (Intent intent) {
    Fragment f = checkFragmentInstance(R.id.fragment_container, ListFragment.class);
    if (f != null) {
      new Handler(getMainLooper()).post(() -> {
        ((ListFragment) f).toggleSearchLabel(false);
        ((ListFragment) f).initNotesList(intent);
      });
    }
  }


  //NavigationDrawerFragment클래스의 onDrawerOpened 메소드에서 사용됨
  //커밋 보류중
  public void commitPending () {
    Fragment f = checkFragmentInstance(R.id.fragment_container, ListFragment.class);
    if (f != null) {
      ((ListFragment) f).commitPending();
    }
  }


  /**
   * Checks if allocated fragment is of the required type and then returns it or returns null
   */
  //할당 된 프래그먼트가 필요한 유형인지 확인한 다음 반환하거나 null을 반환
  private Fragment checkFragmentInstance (int id, Object instanceClass) {
    Fragment result = null;
    Fragment fragment = getFragmentManagerInstance().findFragmentById(id);
    if (fragment != null && instanceClass.equals(fragment.getClass())) {
      result = fragment;
    }
    return result;
  }

  //Back버튼 누를시
  @Override
  public void onBackPressed () {

    // SketchFragment
    Fragment f = checkFragmentInstance(R.id.fragment_container, SketchFragment.class);
    if (f != null) {
      ((SketchFragment) f).save();

      // Removes forced portrait orientation for this fragment
      //화면 회전에 관련된 코드
      setRequestedOrientation(
          ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

      getFragmentManagerInstance().popBackStack();
      return;
    }

    // DetailFragment
    f = checkFragmentInstance(R.id.fragment_container, DetailFragment.class);
    if (f != null) {
      ((DetailFragment) f).goBack = true;
      ((DetailFragment) f).saveAndExit((DetailFragment) f);
      return;
    }

    // ListFragment
    f = checkFragmentInstance(R.id.fragment_container, ListFragment.class);
    if (f != null) {
      // Before exiting from app the navigation drawer is opened
      if (prefs.getBoolean("settings_navdrawer_on_exit", false) && getDrawerLayout() != null &&
          !getDrawerLayout().isDrawerOpen(GravityCompat.START)) {
        getDrawerLayout().openDrawer(GravityCompat.START);
      } else if (!prefs.getBoolean("settings_navdrawer_on_exit", false) && getDrawerLayout() != null &&
          getDrawerLayout().isDrawerOpen(GravityCompat.START)) {
        getDrawerLayout().closeDrawer(GravityCompat.START);
      } else {
        if (!((ListFragment) f).closeFab()) {
          isPasswordAccepted = false;
          super.onBackPressed();
        }
      }
      return;
    }
    super.onBackPressed();
  }


  //액티비티가 종료되는 경우에 데이터를 저장할 수 있게해줌
  @Override
  public void onSaveInstanceState (Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString("navigationTmp", navigationTmp);
  }


  //액티비티 생명주기 Pause시에 호출
  @Override
  protected void onPause () {
    super.onPause();
    Crouton.cancelAllCroutons();
  }


  //DrawerLayout 호출? - DrawerLayout - 서랍형식으로(슬라이딩 메뉴) 열리는 레이아웃
  public DrawerLayout getDrawerLayout () {
    return drawerLayout;
  }


  //ActionBarDrawerToggle 은 Navigation Drawer 를 ActionBar 에서 콘트롤하기 쉽도록 제공되는 class 이다
  public ActionBarDrawerToggle getDrawerToggle () {
    if (getFragmentManagerInstance().findFragmentById(R.id.navigation_drawer) != null) {
      return ((NavigationDrawerFragment) getFragmentManagerInstance().findFragmentById(
          R.id.navigation_drawer)).mDrawerToggle;
    } else {
      return null;
    }
  }


  /**
   * Finishes multiselection mode started by ListFragment
   */
  //ListFragment에 의해 시작된 다중선택 모드를 마감하라

  //ActionMode 종료시 호출
  //액션 모드는 특정 상황에 임시적으로 열리는 액션바다 (메모 길게 누르면 다중선택모드로 되는 식)
  public void finishActionMode () {
    ListFragment fragment = (ListFragment) getFragmentManagerInstance().findFragmentByTag(FRAGMENT_LIST_TAG);
    if (fragment != null) {
      fragment.finishActionMode();
    }
  }


  //툴바 가져오기? 툴바 호출?
  Toolbar getToolbar () {
    return toolbar;
  }


  //기능, 목적 모르겠음... 인텐트를 다루는 용도?
  private void handleIntents () {
    Intent i = getIntent();

    if (i.getAction() == null) {
      return;
    }

    if (ACTION_RESTART_APP.equals(i.getAction())) {
      SystemHelper.restartApp(getApplicationContext(), MainActivity.class);
    }

    //receivedIntent() 메소드로 들어온 인자가 있으면
    if (receivedIntent(i)) {
      Note note = i.getParcelableExtra(INTENT_NOTE); //putExtra로 넘긴 Parcelable 받아올때 사용
      if (note == null) {
        note = DbHelper.getInstance().getNote(i.getIntExtra(INTENT_KEY, 0));
      }
      // Checks if the same note is already opened to avoid to open again
      //이미 해당노트가 열려있다면 다시 오픈하지 않도록
      if (note != null && noteAlreadyOpened(note)) {
        return;
      }
      // Empty note instantiation
      if (note == null) {
        note = new Note();
      }
      switchToDetail(note);
      return;
    }

    if (ACTION_SEND_AND_EXIT.equals(i.getAction())) {
      saveAndExit(i);
      return;
    }

    // Tag search
    if (Intent.ACTION_VIEW.equals(i.getAction())) {
      switchToList();
      return;
    }

    // Home launcher shortcut widget
    if (ACTION_SHORTCUT_WIDGET.equals(i.getAction())) {
      switchToDetail(new Note());
      return;
    }
  }


  /**
   * Used to perform a quick text-only note saving (eg. Tasker+Pushbullet)
   */
  //노트 수정 종료와 동시에 저장하면서 빠져나오기위한 메소드
  private void saveAndExit (Intent i) {
    Note note = new Note();
    note.setTitle(i.getStringExtra(Intent.EXTRA_SUBJECT));
    note.setContent(i.getStringExtra(Intent.EXTRA_TEXT));
    DbHelper.getInstance().updateNote(note, true);
    showToast(getString(R.string.note_updated), Toast.LENGTH_SHORT);
    finish();
  }


  //인텐트 받기 - 노티바, 위젯 등 클릭 시 호출
  private boolean receivedIntent (Intent i) {
    return ACTION_SHORTCUT.equals(i.getAction())
        || ACTION_NOTIFICATION_CLICK.equals(i.getAction())
        || ACTION_WIDGET.equals(i.getAction())
        || ACTION_WIDGET_TAKE_PHOTO.equals(i.getAction())
        || ((Intent.ACTION_SEND.equals(i.getAction())
        || Intent.ACTION_SEND_MULTIPLE.equals(i.getAction())
        || INTENT_GOOGLE_NOW.equals(i.getAction()))
        && i.getType() != null)
        || i.getAction().contains(ACTION_NOTIFICATION_CLICK);
  }


  //메모가 이미 열려있을 때
  private boolean noteAlreadyOpened (Note note) {
    DetailFragment detailFragment = (DetailFragment) getFragmentManagerInstance().findFragmentByTag(
        FRAGMENT_DETAIL_TAG);
    return detailFragment != null && NotesHelper.haveSameId(note, detailFragment.getCurrentNote());
  }


  //모르겠음, 아마 메모 리스트 화면으로 돌아가는 메소드
  public void switchToList () {
    FragmentTransaction transaction = getFragmentManagerInstance().beginTransaction();
    animateTransition(transaction, TRANSITION_HORIZONTAL);
    ListFragment mListFragment = new ListFragment();
    transaction.replace(R.id.fragment_container, mListFragment, FRAGMENT_LIST_TAG).addToBackStack
        (FRAGMENT_DETAIL_TAG).commitAllowingStateLoss();
    if (getDrawerToggle() != null) {
      getDrawerToggle().setDrawerIndicatorEnabled(false);
    }
    getFragmentManagerInstance().getFragments();
    EventBus.getDefault().post(new SwitchFragmentEvent(SwitchFragmentEvent.Direction.PARENT));
  }


  //아마 메모 내용화면으로 돌아가는 메소드
  public void switchToDetail (Note note) {
    FragmentTransaction transaction = getFragmentManagerInstance().beginTransaction();
    animateTransition(transaction, TRANSITION_HORIZONTAL);
    DetailFragment mDetailFragment = new DetailFragment();
    Bundle b = new Bundle();
    b.putParcelable(INTENT_NOTE, note);
    mDetailFragment.setArguments(b);
    if (getFragmentManagerInstance().findFragmentByTag(FRAGMENT_DETAIL_TAG) == null) {
      transaction.replace(R.id.fragment_container, mDetailFragment, FRAGMENT_DETAIL_TAG)
                 .addToBackStack(FRAGMENT_LIST_TAG)
                 .commitAllowingStateLoss();
    } else {
      getFragmentManagerInstance().popBackStackImmediate();
      transaction.replace(R.id.fragment_container, mDetailFragment, FRAGMENT_DETAIL_TAG)
                 .addToBackStack(FRAGMENT_DETAIL_TAG)
                 .commitAllowingStateLoss();
    }
  }


  /**
   * Notes sharing
   */
  //노트 공유 기능
  public void shareNote (Note note) {

    String titleText = note.getTitle();

    String contentText = titleText
        + System.getProperty("line.separator")
        + note.getContent();

    Intent shareIntent = new Intent();
    // Prepare sharing intent with only text
    if (note.getAttachmentsList().isEmpty()) {
      shareIntent.setAction(Intent.ACTION_SEND);
      shareIntent.setType("text/plain");

      // Intent with single image attachment
    } else if (note.getAttachmentsList().size() == 1) {
      shareIntent.setAction(Intent.ACTION_SEND);
      Attachment attachment = note.getAttachmentsList().get(0);
      shareIntent.setType(attachment.getMime_type());
      shareIntent.putExtra(Intent.EXTRA_STREAM, FileProviderHelper.getShareableUri(attachment));

      // Intent with multiple images
    } else if (note.getAttachmentsList().size() > 1) {
      shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
      ArrayList<Uri> uris = new ArrayList<>();
      // A check to decide the mime type of attachments to share is done here
      HashMap<String, Boolean> mimeTypes = new HashMap<>();
      for (Attachment attachment : note.getAttachmentsList()) {
        uris.add(FileProviderHelper.getShareableUri(attachment));
        mimeTypes.put(attachment.getMime_type(), true);
      }
      // If many mime types are present a general type is assigned to intent
      if (mimeTypes.size() > 1) {
        shareIntent.setType("*/*");
      } else {
        shareIntent.setType((String) mimeTypes.keySet().toArray()[0]);
      }

      shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
    }
    shareIntent.putExtra(Intent.EXTRA_SUBJECT, titleText);
    shareIntent.putExtra(Intent.EXTRA_TEXT, contentText);

    startActivity(Intent.createChooser(shareIntent, getResources().getString(R.string.share_message_chooser)));
  }


  /**
   * Single note permanent deletion
   *
   * @param note Note to be deleted
   */

  //메모 삭제
  public void deleteNote (Note note) {
    new NoteProcessorDelete(Collections.singletonList(note)).process();
    BaseActivity.notifyAppWidgets(this);
    LogDelegate.d("Deleted permanently note with ID '" + note.get_id() + "'");
  }


  //위젯 변경시 업데이트
  public void updateWidgets () {
    new UpdateWidgetsTask(getApplicationContext())
        .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }


  public void showMessage (int messageId, Style style) {
    showMessage(getString(messageId), style);
  }


  //모르겠음
  public void showMessage (String message, Style style) {
    // ViewGroup used to show Crouton keeping compatibility with the new Toolbar
    runOnUiThread(() -> Crouton.makeText(this, message, style, croutonViewContainer).show());
  }

  //모르겠음
  @Override
  public void onSharedPreferenceChanged (SharedPreferences sharedPreferences, String key) {
    prefsChanged = true;
  }

}
