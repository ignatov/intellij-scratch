package org.intellij.scratch;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.generate.tostring.util.StringUtil;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author ignatov
 */
public class NewScratchFileAction extends AnAction implements DumbAware {
  private static final Key<Language> SCRATCH_LANGUAGE = Key.create("SCRATCH_LANGUAGE");

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;

    MyDialog dialog = new MyDialog(project);
    dialog.show();

    if (dialog.isOK()) {
      Language language = dialog.getType();
      project.putUserData(SCRATCH_LANGUAGE, language);
      LanguageFileType associatedFileType = language.getAssociatedFileType();
      String defaultExtension = associatedFileType != null ? associatedFileType.getDefaultExtension() : "unknown";
      VirtualFile virtualFile = new LightVirtualFile("scratch." + defaultExtension, language, "") { // todo: name clash
        @NotNull
        @Override
        public VirtualFileSystem getFileSystem() {
          return ScratchFileSystem.getScratchFileSystem();
        }

        @Override
        public String getPath() {
          return "/" + project.getLocationHash() + super.getPath();
        }
      };
      ScratchFileSystem.getScratchFileSystem().addFile(virtualFile);
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile);
      FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    }
  }

  public static class ScratchFileSystem extends DummyFileSystem {
    private static final String PROTOCOL = "scratchDummy";
    private final Map<String, VirtualFile> myCachedFiles = new HashMap<String, VirtualFile>();

    private ProjectManagerAdapter myProjectManagerListener = new ProjectManagerAdapter() {
      @Override
      public void projectClosed(Project project) {
        String hash = project.getLocationHash();
        Set<String> toRemove = ContainerUtil.newHashSet();
        for (String path : myCachedFiles.keySet()) {
          if (path.contains(hash)) {
            toRemove.add(path);
          }
        }
        for (String s : toRemove) {
          myCachedFiles.remove(s);
        }
      }
    };

    public static ScratchFileSystem getScratchFileSystem() {
      return (ScratchFileSystem) VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
    }

    public ScratchFileSystem() {
      ProjectManager.getInstance().addProjectManagerListener(myProjectManagerListener);
    }

    @Override
    public VirtualFile findFileByPath(@NotNull String path) {
      VirtualFile file = myCachedFiles.get(path);
      if (file != null && file.isValid()) return file;
      return null;
    }

    public void addFile(@NotNull VirtualFile file) {
      myCachedFiles.put(file.getPath(), file);
    }

    @NotNull
    @Override
    public String getProtocol() {
      return PROTOCOL;
    }
  }

  private static class MyDialog extends DialogWrapper {
    @Nullable
    private Project myProject;
    @NotNull
    private JComboBox myComboBox;

    protected MyDialog(@Nullable Project project) {
      super(project);
      myProject = project;
      setTitle("Specify the Language");
      init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel(new BorderLayout());
      myComboBox = createCombo(getLanguages());
      panel.add(myComboBox, BorderLayout.CENTER);
      return panel;
    }

    public Language getType() {
      return ((Language) myComboBox.getSelectedItem());
    }

    private JComboBox createCombo(List<Language> languages) {
      JComboBox jComboBox = new ComboBox(new CollectionComboBoxModel(languages));
      jComboBox.setRenderer(new ListCellRendererWrapper<Language>() {
        @Override
        public void customize(JList list, Language lang, int index, boolean selected, boolean hasFocus) {
          if (lang != null) {
            setText(lang.getDisplayName());
            LanguageFileType associatedLanguage = lang.getAssociatedFileType();
            if (associatedLanguage != null) setIcon(associatedLanguage.getIcon());
          }
        }
      });
      new ComboboxSpeedSearch(jComboBox) {
        @Override
        protected String getElementText(Object element) {
          return element instanceof Language ? ((Language) element).getDisplayName() : null;
        }
      };
      Language previous = myProject != null ? myProject.getUserData(SCRATCH_LANGUAGE) : null;
      if (previous != null) {
        jComboBox.setSelectedItem(previous);
      }

      return jComboBox;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myComboBox;
    }
  }

  private static List<Language> getLanguages() {
    Set<Language> result = ContainerUtil.newTreeSet(new Comparator<Language>() {
      @Override
      public int compare(Language l1, Language l2) {
        return l1.getDisplayName().compareTo(l2.getDisplayName());
      }
    });
    for (Language lang : Language.getRegisteredLanguages()) {
      if (!StringUtil.isEmpty(lang.getDisplayName())) result.add(lang);
      for (Language dialect : lang.getDialects()) {
        if (!"$XSLT".equals(dialect.getDisplayName())) result.add(dialect);
      }
    }
    return ContainerUtil.newArrayList(result);
  }
}
