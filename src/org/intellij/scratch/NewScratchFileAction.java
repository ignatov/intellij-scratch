package org.intellij.scratch;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * @author ignatov
 */
public class NewScratchFileAction extends AnAction implements DumbAware {
    private static final Key<FileType> SCRATCH_FILE_TYPE = Key.create("SCRATCH_FILE_TYPE");

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        MyDialog myDialog = new MyDialog(project);
        myDialog.show();

        if (myDialog.isOK()) {
            FileType type = myDialog.getType();
            project.putUserData(SCRATCH_FILE_TYPE, type);
            VirtualFile virtualFile = new LightVirtualFile("scratch." + type.getDefaultExtension(), type, "");
            OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile);
            FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
        }
    }

    private static class MyDialog extends DialogWrapper {
        @Nullable private Project myProject;
        @NotNull private JComboBox myComboBox;

        protected MyDialog(@Nullable Project project) {
            super(project);
            myProject = project;
            setTitle("Specify the Type");
            init();
        }

        @Nullable
        @Override
        protected JComponent createCenterPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            myComboBox = createCombo(getFileTypes());
            panel.add(myComboBox, BorderLayout.CENTER);
            return panel;
        }

        public FileType getType() {
            return ((FileType) myComboBox.getSelectedItem());
        }

        private JComboBox createCombo(List<FileType> mySourceWrappers) {
            JComboBox jComboBox = new ComboBox(new CollectionComboBoxModel(mySourceWrappers));
            jComboBox.setRenderer(new ListCellRendererWrapper<FileType>() {
                @Override
                public void customize(JList list, FileType value, int index, boolean selected, boolean hasFocus) {
                    if (value != null) {
                        setText(value.getName());
                        setIcon(value.getIcon());
                    }
                }
            });
            new ComboboxSpeedSearch(jComboBox) {
                @Override
                protected String getElementText(Object element) {
                    return element instanceof FileType ? ((FileType) element).getName() : null;
                }
            };
            FileType previous = myProject != null ? myProject.getUserData(SCRATCH_FILE_TYPE) : null;
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

    private static List<FileType> getFileTypes() {
        final Set<FileType> allFileTypes = ContainerUtil.newHashSet();
        Collections.addAll(allFileTypes, FileTypeManager.getInstance().getRegisteredFileTypes());
        for (Language language : Language.getRegisteredLanguages()) {
            final FileType fileType = language.getAssociatedFileType();
            if (fileType != null) {
                allFileTypes.add(fileType);
            }
        }
        List<FileType> mySourceWrappers = ContainerUtil.newArrayList();

        for (FileType fileType : allFileTypes) {
            if (fileType != StdFileTypes.GUI_DESIGNER_FORM &&
                fileType != StdFileTypes.IDEA_MODULE &&
                fileType != StdFileTypes.IDEA_PROJECT &&
                fileType != StdFileTypes.IDEA_WORKSPACE &&
                fileType != FileTypes.ARCHIVE &&
                fileType != FileTypes.UNKNOWN &&
                !(fileType instanceof AbstractFileType) &&
                !fileType.isBinary() &&
                !fileType.isReadOnly()) {
                mySourceWrappers.add(fileType);
            }
        }

        Collections.sort(mySourceWrappers, new Comparator<FileType>() {
            @Override
            public int compare(FileType fileType, FileType fileType2) {
                return fileType.getName().compareTo(fileType2.getName());
            }
        });
        return mySourceWrappers;
    }
}
