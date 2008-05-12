/*
 * @(#)BasicFolderChooserUI.java 4/12/2006
 *
 * Copyright 2002 - 2006 JIDE Software Inc. All rights reserved.
 */
package com.jidesoft.plaf.basic;

import com.jidesoft.dialog.ButtonPanel;
import com.jidesoft.plaf.FolderChooserUI;
import com.jidesoft.swing.FolderChooser;
import com.jidesoft.utils.SystemInfo;
import sun.awt.shell.ShellFolder;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicFileChooserUI;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

public class BasicFolderChooserUI extends BasicFileChooserUI implements FolderChooserUI {
    private FolderChooser _folderChooser;

    private FolderToolBar _toolbar;
    private JTree _fileSystemTree;
    private JScrollPane _treeScrollPane;

    private JButton _approveButton;
    private JButton _cancelButton;
    private JPanel _buttonPanel;

    private Action _approveSelectionAction = new ApproveSelectionAction();
    public BasicFolderChooserUI.FolderChooserSelectionListener _selectionListener;

    public BasicFolderChooserUI(FolderChooser chooser) {
        super(chooser);
        BasicFileSystemTreeNode.clearCache();
    }

    public static ComponentUI createUI(JComponent c) {
        return new BasicFolderChooserUI((FolderChooser) c);
    }

    @Override
    public void installComponents(JFileChooser chooser) {
        _folderChooser = (FolderChooser) chooser;

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(createFileSystemTreePanel(), BorderLayout.CENTER);
        panel.add(createToolbar(), BorderLayout.BEFORE_FIRST_LINE);
        panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        chooser.add(panel);

        chooser.setLayout(new BorderLayout());

        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        Component accessory = chooser.getAccessory();
        if (accessory != null) {
            chooser.add(chooser.getAccessory(), BorderLayout.BEFORE_FIRST_LINE);
        }

        chooser.add(panel, BorderLayout.CENTER);
        chooser.add(_buttonPanel = createButtonPanel(), BorderLayout.AFTER_LAST_LINE);

        updateView(chooser);

        Runnable runnable = new Runnable() {
            public void run() {
                _fileSystemTree.requestFocusInWindow();
            }
        };
        SwingUtilities.invokeLater(runnable);
    }

    protected JPanel createButtonPanel() {
        _approveButton = new JButton();
        _approveButton.setAction(getApproveSelectionAction());

        _cancelButton = new JButton();
        _cancelButton.addActionListener(getCancelSelectionAction());

        ButtonPanel buttonPanel = new ButtonPanel();
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 6, 6, 6));
        buttonPanel.addButton(_approveButton, ButtonPanel.AFFIRMATIVE_BUTTON);
        buttonPanel.addButton(_cancelButton, ButtonPanel.CANCEL_BUTTON);
        return buttonPanel;
    }

    @Override
    public void rescanCurrentDirectory(JFileChooser fc) {
        super.rescanCurrentDirectory(fc);
    }

    @Override
    public void ensureFileIsVisible(JFileChooser fc, File f) {
        super.ensureFileIsVisible(fc, f);
        ensureFileIsVisible(f, true);
    }

    protected JComponent createToolbar() {
        _toolbar = new FolderToolBar(true, _folderChooser.getRecentList());
        _toolbar.addListener(new FolderToolBarListener() {
            // ------------------------------------------------------------------------------
            // Implementation of FolderToolBarListener
            // ------------------------------------------------------------------------------

            public void deleteFolderButtonClicked() {
                // make sure user really wants to do this
                String text, header;
                TreePath path = _fileSystemTree.getSelectionPaths()[0];
                java.util.List selection = getSelectedFolders(new TreePath[]{path});

                final ResourceBundle resourceBundle = FolderChooserResource.getResourceBundle(Locale.getDefault());
                if (selection.size() > 1) {
                    text = MessageFormat.format(
                            resourceBundle.getString("FolderChooser.delete.message2"), selection.size());
                }
                else {
                    text = resourceBundle.getString("FolderChooser.delete.message1");
                }
                final String title = resourceBundle.getString("FolderChooser.delete.title");

                int result = JOptionPane.showConfirmDialog(_folderChooser, text, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                if (result == JOptionPane.OK_OPTION) {
                    TreePath parentPath = path.getParentPath();
                    Object parentObject = parentPath.getLastPathComponent();
                    Object deletedObject = path.getLastPathComponent();
                    int index = _fileSystemTree.getModel().getIndexOfChild(parentObject, deletedObject);
                    for (int i = 0; i < selection.size(); i++) {
                        File f = (File) selection.get(i);
                        recursiveDelete(f);
                    }
                    ((BasicFileSystemTreeModel) _fileSystemTree.getModel()).removePath(path, index, deletedObject);
                    TreePath pathToSelect = parentPath;
                    if (index >= ((MutableTreeNode) parentObject).getChildCount()) {
                        index = ((MutableTreeNode) parentObject).getChildCount() - 1;
                    }
                    if (index > 0) {
                        pathToSelect = parentPath.pathByAddingChild(((MutableTreeNode) parentObject).getChildAt(index));
                    }
                    _fileSystemTree.setSelectionPath(pathToSelect);
                    _fileSystemTree.scrollPathToVisible(pathToSelect);
                }

            }

            /**
             * Recursively deletes a file/directory.
             *
             * @param file The file/folder to delete
             * @return <code>true</code> only if the file and all children were successfully deleted.
             */
            public final boolean recursiveDelete(File file) {
                if (isFileSystem(file) && file.isDirectory()) {
                    // delete all children first
                    File[] children = FileSystemView.getFileSystemView().getFiles(file, false);
                    for (int i = 0; i < children.length; i++) {
                        File f = children[i];
                        if (!recursiveDelete(f)) {
                            return false;
                        }
                    }
                    // delete this file.
                    return file.delete();
                }
                else {
                    return false;
                }
            }

            public void newFolderButtonClicked() {
                // get the selected folder
                TreePath[] paths = _fileSystemTree.getSelectionPaths();
                java.util.List selection = getSelectedFolders(paths);
                if (selection.size() > 1 || selection.size() == 0)
                    return; // should never happen

                File parent = (File) selection.get(0);

                final ResourceBundle resourceBundle = FolderChooserResource.getResourceBundle(Locale.getDefault());
                String folderName = JOptionPane.showInputDialog(_folderChooser, resourceBundle.getString("FolderChooser.new.folderName"),
                        resourceBundle.getString("FolderChooser.new.title"), JOptionPane.OK_CANCEL_OPTION | JOptionPane.QUESTION_MESSAGE);

                if (folderName != null) {
                    File newFolder = new File(parent, folderName);
                    boolean success = newFolder.mkdir();

                    TreePath parentPath = paths[0];
                    boolean isExpanded = _fileSystemTree.isExpanded(parentPath);
                    if (!isExpanded) { // expand it first
                        _fileSystemTree.expandPath(parentPath);
                    }

                    LazyMutableTreeNode parentTreeNode = (LazyMutableTreeNode) parentPath.getLastPathComponent();
                    BasicFileSystemTreeNode child = BasicFileSystemTreeNode.createFileSystemTreeNode(newFolder, _folderChooser);
//                    child.setParent(parentTreeNode);
                    if (success) {
                        parentTreeNode.clear();
                        int insertIndex = _fileSystemTree.getModel().getIndexOfChild(parentTreeNode, child);
                        if (insertIndex != -1) {
//                            ((BasicFileSystemTreeModel) _fileSystemTree.getModel()).insertNodeInto(child, parentTreeNode, insertIndex);
                            ((BasicFileSystemTreeModel) _fileSystemTree.getModel()).nodeStructureChanged(parentTreeNode);
//                            ((BasicFileSystemTreeModel) _fileSystemTree.getModel()).addPath(parentPath, insertIndex, child);
                        }
                    }
                    TreePath newPath = parentPath.pathByAddingChild(child);
                    _fileSystemTree.setSelectionPath(newPath);
                    _fileSystemTree.scrollPathToVisible(newPath);
                }
            }

            public void myDocumentsButtonClicked() {
                File myDocuments = FileSystemView.getFileSystemView().getDefaultDirectory();
                ensureFileIsVisible(myDocuments, true);
            }

            public void desktopButtonClicked() {
                File desktop = FileSystemView.getFileSystemView().getHomeDirectory();
                ensureFileIsVisible(desktop, true);
            }

            public void recentFolderSelected(final File file) {
                new Thread(new Runnable() {
                    public void run() {
                        setWaitCursor(true);
                        try {
                            ensureFileIsVisible(file, true);
                        }
                        finally {
                            setWaitCursor(false);
                        }
                    }
                }).start();
            }

            private Cursor m_oldCursor;

            private void setWaitCursor(boolean isWait) {
                Window parentWindow = SwingUtilities.getWindowAncestor(_folderChooser);
                if (isWait) {
                    Cursor hourglassCursor = new Cursor(Cursor.WAIT_CURSOR);
                    m_oldCursor = parentWindow.getCursor();
                    parentWindow.setCursor(hourglassCursor);
                }
                else {
                    if (m_oldCursor != null) {
                        parentWindow.setCursor(m_oldCursor);
                        m_oldCursor = null;
                    }
                }
            }


            public java.util.List getSelectedFolders() {
                TreePath[] paths = _fileSystemTree.getSelectionPaths();
                return getSelectedFolders(paths);
            }

            public java.util.List getSelectedFolders(TreePath[] paths) {
                if (paths == null || paths.length == 0)
                    return new ArrayList();

                List folders = new ArrayList(paths.length);
                for (int i = 0; i < paths.length; i++) {
                    TreePath path = paths[i];
                    BasicFileSystemTreeNode f = (BasicFileSystemTreeNode) path.getLastPathComponent();
                    folders.add(f.getFile());
                }
                return folders;
            }

        });
        updateToolbarButtons();
        return _toolbar;
    }

    /**
     * Updates toolbar button status depending on current selection status
     */
    protected void updateToolbarButtons() {
        // delete folder button
        TreePath[] selectedFiles = _fileSystemTree == null ? new TreePath[0] : _fileSystemTree.getSelectionPaths();
//        System.out.println("selectedFiles.length = " + selectedFiles == null ? 0 : selectedFiles.length);
        if (selectedFiles != null && selectedFiles.length > 0) {
            _toolbar.enableDelete();
        }
        else {
            _toolbar.disableDelete();
        }

        // new folder button (only enable if exactly one folder selected
        if (selectedFiles != null && selectedFiles.length == 1) {
            _toolbar.enableNewFolder();
        }
        else {
            _toolbar.disableNewFolder();
        }
    }

    private JComponent createFileSystemTreePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        _fileSystemTree = new BasicFileSystemTree(_folderChooser);
        updateMultiSelectionEnabled();
        _treeScrollPane = new JScrollPane(_fileSystemTree);
        panel.add(_treeScrollPane);
        return panel;
    }

    private void updateMultiSelectionEnabled() {
        if (_folderChooser.isMultiSelectionEnabled()) {
            _fileSystemTree.getSelectionModel().setSelectionMode(
                    TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        }
        else {
            _fileSystemTree.getSelectionModel().setSelectionMode(
                    TreeSelectionModel.SINGLE_TREE_SELECTION);
        }
    }

    @Override
    public void uninstallComponents(JFileChooser chooser) {
        chooser.remove(_treeScrollPane);
        chooser.remove(_buttonPanel);
    }

    @Override
    protected void installListeners(JFileChooser fc) {
        super.installListeners(fc);
        _selectionListener = new FolderChooserSelectionListener();
        _fileSystemTree.addTreeSelectionListener(_selectionListener);
    }

    @Override
    protected void uninstallListeners(JFileChooser fc) {
        super.uninstallListeners(fc);
        _fileSystemTree.removeTreeSelectionListener(_selectionListener);
    }

    @Override
    public PropertyChangeListener createPropertyChangeListener(JFileChooser fc) {
        return new FolderChooserPropertyChangeListener();
    }

    private void updateView(JFileChooser chooser) {
        if (chooser.getApproveButtonText() != null) {
            _approveButton.setText(chooser.getApproveButtonText());
            _approveButton.setMnemonic(chooser.getApproveButtonMnemonic());
        }
        else {
            if (JFileChooser.OPEN_DIALOG == chooser.getDialogType()) {
                _approveButton.setText(openButtonText);
                _approveButton.setToolTipText(openButtonToolTipText);
                _approveButton.setMnemonic(openButtonMnemonic);
            }
            else {
                _approveButton.setText(saveButtonText);
                _approveButton.setToolTipText(saveButtonToolTipText);
                _approveButton.setMnemonic(saveButtonMnemonic);
            }
        }

        _cancelButton.setText(cancelButtonText);
        _cancelButton.setMnemonic(cancelButtonMnemonic);

        _buttonPanel.setVisible(chooser.getControlButtonsAreShown());
    }

    /**
     * Checks if <code>f</code> represents a real directory or file as opposed to a special folder such as
     * <code>"Desktop"</code>. Used by UI classes to decide if a folder is selectable when doing directory choosing.
     *
     * @param f a <code>File</code> object
     * @return <code>true</code> if <code>f</code> is a real file or directory.
     */
    public static boolean isFileSystem(File f) {
        if (f instanceof ShellFolder) {
            ShellFolder sf = (ShellFolder) f;
            // Shortcuts to directories are treated as not being file system objects,
            // so that they are never returned by JFileChooser.
            return sf.isFileSystem() && !(sf.isLink() && sf.isDirectory());
        }
        else {
            return true;
        }
    }

    private TreePath getTreePathForFile(File file) {
        if (!file.isDirectory()) {
            return null;
        }
        Stack stack = new Stack();
        List list = new ArrayList();
        list.add(_fileSystemTree.getModel().getRoot());
        FileSystemView fsv = _folderChooser.getFileSystemView();
        File[] alternativeRoots = null;
        File root = null;
        if (SystemInfo.isWindows()) {
            File[] roots = fsv.getRoots();
            root = roots[0];
            if (isFileSystem(root) && root.isDirectory()) {
                alternativeRoots = root.listFiles();
            }
        }
        File parent = file;
        outloop:
        do {
            stack.push(parent);
            if (alternativeRoots != null) {
                for (int i = 0; i < alternativeRoots.length; i++) {
                    File r = alternativeRoots[i];
                    if (r.equals(parent)) {
                        stack.push(root);
                        break outloop;
                    }
                }
            }
            parent = _folderChooser.getFileSystemView().getParentDirectory(parent);
        }
        while (parent != null);

        while (!stack.empty()) {
            list.add(BasicFileSystemTreeNode.createFileSystemTreeNode((File) stack.pop(), _folderChooser));
        }
        return new TreePath(list.toArray());
    }

    private void ensureFileIsVisible(File file, boolean scroll) {
        final TreePath path = file == null ? new TreePath(_fileSystemTree.getModel().getRoot()) : getTreePathForFile(file);
        if (path != null) {
            _fileSystemTree.setSelectionPath(path);
            _fileSystemTree.expandPath(path);
            if (scroll) {
                Runnable runnable = new Runnable() {
                    public void run() {
                        _fileSystemTree.scrollPathToVisible(path);
                    }
                };
                SwingUtilities.invokeLater(runnable);
            }
        }
//        getApproveSelectionAction().setEnabled(_fileSystemTree.getSelectionCount() > 0);
    }

    private class FolderChooserPropertyChangeListener implements PropertyChangeListener {
        public void propertyChange(PropertyChangeEvent evt) {
            if (FolderChooser.PROPERTY_RECENTLIST.equals(evt.getPropertyName())) {
                _toolbar.setRecentList((List) evt.getNewValue());
            }
            else if (JFileChooser.APPROVE_BUTTON_TEXT_CHANGED_PROPERTY.equals(evt.getPropertyName())) {
                updateView(_folderChooser);
            }
            else if (JFileChooser.DIALOG_TYPE_CHANGED_PROPERTY.equals(evt.getPropertyName())) {
                updateView(_folderChooser);
            }
            else if (JFileChooser.MULTI_SELECTION_ENABLED_CHANGED_PROPERTY.equals(evt.getPropertyName())) {
                updateMultiSelectionEnabled();
            }
            else if (JFileChooser.DIRECTORY_CHANGED_PROPERTY.equals(evt.getPropertyName())) {
                ensureFileIsVisible(_folderChooser.getCurrentDirectory(), true);
            }
            else if (JFileChooser.ACCESSORY_CHANGED_PROPERTY.equals(evt.getPropertyName())) {
                Component oldValue = (Component) evt.getOldValue();
                Component newValue = (Component) evt.getNewValue();
                if (oldValue != null) {
                    _folderChooser.remove(oldValue);
                }
                if (newValue != null) {
                    _folderChooser.add(newValue, BorderLayout.BEFORE_FIRST_LINE);
                }
                _folderChooser.revalidate();
                _folderChooser.repaint();
            }
            else if (JFileChooser.CONTROL_BUTTONS_ARE_SHOWN_CHANGED_PROPERTY.equals(evt.getPropertyName())) {
                updateView(_folderChooser);
            }
        }
    }

    private class FolderChooserSelectionListener implements TreeSelectionListener {
        public void valueChanged(TreeSelectionEvent e) {
            getApproveSelectionAction().setEnabled(_fileSystemTree.getSelectionCount() > 0);
            if (_toolbar != null) {
                updateToolbarButtons();
            }

            /*
             * Added on 05/11/2008 in response to http://www.jidesoft.com/forum/viewtopic.php?p=26932#26932
             *
             * The addition below ensures Component#firePropertyChange is called, and thus fires the
             * appropriate 'bound property event' on all folder selection changes.
             *
             * @see FolderChooser#setSelectedFolder(folder)
             */

            String folderPath = (e.getNewLeadSelectionPath().getLastPathComponent()).toString();
            File folder = new File(folderPath);
            _folderChooser.setSelectedFolder(folder);

            /*
             * End of addition.
             *
             * Added on 05/11/2008 in response to http://www.jidesoft.com/forum/viewtopic.php?p=26932#26932
             */
        }
    }

    private void setSelectedFiles() {
        TreePath[] selectedPaths = _fileSystemTree.getSelectionPaths();
        if (selectedPaths == null || selectedPaths.length == 0) {
            _folderChooser.setSelectedFile(null);
            return;
        }

        java.util.List files = new ArrayList();
        for (int i = 0, c = selectedPaths.length; i < c; i++) {
            File f = ((BasicFileSystemTreeNode) selectedPaths[i].getLastPathComponent()).getFile();
            files.add(f);
        }

        _folderChooser.setSelectedFiles((File[]) files.toArray(new File[files.size()]));
    }

    @Override
    public Action getApproveSelectionAction() {
        return _approveSelectionAction;
    }

    private class ApproveSelectionAction extends AbstractAction {
        public ApproveSelectionAction() {
            setEnabled(false);
        }

        public void actionPerformed(ActionEvent e) {
            setSelectedFiles();
            _folderChooser.approveSelection();
        }
    }
}
