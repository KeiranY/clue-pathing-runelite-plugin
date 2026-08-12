package com.shortestclue.debug;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.ui.PluginPanel;

public class CluePickerPanel extends PluginPanel
{
	static class ClueEntry
	{
		private final String tier;
		private final String type;
		private final String label;
		private final Set<WorldPoint> dests;
		private final ClueScroll clue;
		private final String searchText;
		private final String failure;

		ClueEntry(String tier, String type, String label, Set<WorldPoint> dests, ClueScroll clue, String failure)
		{
			this.tier = tier;
			this.type = type;
			this.label = label;
			this.dests = dests;
			this.clue = clue;
			this.failure = failure;
			this.searchText = (type + " " + tier + " " + label).toLowerCase();
		}

		String getTier()
		{
			return this.tier;
		}

		String getType()
		{
			return this.type;
		}

		String getLabel()
		{
			return this.label;
		}

		Set<WorldPoint> getDests()
		{
			return this.dests;
		}

		ClueScroll getClue()
		{
			return this.clue;
		}

		String getFailure()
		{
			return this.failure;
		}

		boolean matches(String[] tokens)
		{
			if (tokens.length == 0)
			{
				return true;
			}
			for (String token : tokens)
			{
				if (!this.searchText.contains(token))
				{
					return false;
				}
			}
			return true;
		}
	}

	private static final String[] TIER_ORDER = {
		"Beginner", "Easy", "Medium", "Hard", "Elite", "Master", "Unknown"
	};

	private static class CheckableItem
	{
		private final String label;
		private boolean selected;

		CheckableItem(String label)
		{
			this.label = label;
			this.selected = true;
		}

		String getLabel()
		{
			return this.label;
		}

		boolean isSelected()
		{
			return this.selected;
		}

		void setSelected(boolean selected)
		{
			this.selected = selected;
		}
	}

	private static class MultiSelectComboBox extends JComboBox<CheckableItem>
	{
		private final String prefix;
		private final Runnable onToggle;
		private boolean keepPopupOpen;

		MultiSelectComboBox(String prefix, List<String> options, Runnable onToggle)
		{
			super(options.stream().map(CheckableItem::new).toArray(CheckableItem[]::new));
			this.prefix = prefix;
			this.onToggle = onToggle;
			setRenderer(new CheckBoxRenderer());
			addActionListener(e ->
			{
				if (isPopupVisible())
				{
					CheckableItem item = (CheckableItem) getSelectedItem();
					if (item != null)
					{
						item.setSelected(!item.isSelected());
					}
					this.keepPopupOpen = true;
					repaint();
					this.onToggle.run();
				}
			});
		}

		boolean isSelected(String label)
		{
			for (int i = 0; i < getItemCount(); i++)
			{
				CheckableItem item = getItemAt(i);
				if (item.getLabel().equals(label))
				{
					return item.isSelected();
				}
			}
			return false;
		}

		@Override
		public void setPopupVisible(boolean v)
		{
			if (this.keepPopupOpen)
			{
				this.keepPopupOpen = false;
				return;
			}
			super.setPopupVisible(v);
		}

		private String buttonText()
		{
			List<String> selected = new ArrayList<>();
			for (int i = 0; i < getItemCount(); i++)
			{
				CheckableItem item = getItemAt(i);
				if (item.isSelected())
				{
					selected.add(item.getLabel());
				}
			}
			if (selected.isEmpty())
			{
				return this.prefix + ": None";
			}
			if (selected.size() == getItemCount())
			{
				return this.prefix + ": All";
			}
			return this.prefix + ": " + String.join(", ", selected);
		}

		private class CheckBoxRenderer implements ListCellRenderer<CheckableItem>
		{
			private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
			private final JCheckBox checkBox = new JCheckBox();
			private final JLabel label = new JLabel();

			CheckBoxRenderer()
			{
				this.panel.add(this.checkBox);
				this.panel.add(this.label);
				this.panel.setOpaque(true);
			}

			@Override
			public Component getListCellRendererComponent(JList<? extends CheckableItem> list, CheckableItem value, int index, boolean isSelected, boolean cellHasFocus)
			{
				if (index == -1)
				{
					this.label.setText(MultiSelectComboBox.this.buttonText());
					this.checkBox.setSelected(true);
					this.label.setForeground(list.getForeground());
					this.panel.setOpaque(false);
					return this.panel;
				}

				this.checkBox.setSelected(value.isSelected());
				this.label.setText(value.getLabel());
				this.label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
				this.panel.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
				return this.panel;
			}
		}
	}

	private final DefaultListModel<ClueEntry> listModel = new DefaultListModel<>();
	private final Consumer<ClueScroll> onSelect;
	private final Runnable onClear;
	private final Consumer<List<ClueEntry>> onFilterChange;
	private final HintTextField searchField = new HintTextField("Search clues...");
	private final JList<ClueEntry> clueList = new JList<>(this.listModel);
	private final JLabel statusLabel = new JLabel();
	private final JLabel countLabel = new JLabel();

	private final MultiSelectComboBox tierFilter;
	private final MultiSelectComboBox typeFilter;
	private final List<ClueEntry> allClues = new ArrayList<>();

	private ClueScroll currentFake;

	public CluePickerPanel(List<ClueEntry> clues, Consumer<ClueScroll> onSelect, Runnable onClear, Consumer<List<ClueEntry>> onFilterChange)
	{
		this.onSelect = onSelect;
		this.onClear = onClear;
		this.onFilterChange = onFilterChange;
		this.allClues.addAll(clues);

		List<String> tierNames = new ArrayList<>();
		for (String tier : TIER_ORDER)
		{
			if (clues.stream().anyMatch(e -> tier.equals(e.getTier())))
			{
				tierNames.add(tier);
			}
		}
		List<String> typeNames = new ArrayList<>();
		for (ClueEntry entry : clues)
		{
			if (!typeNames.contains(entry.getType()))
			{
				typeNames.add(entry.getType());
			}
		}
		this.tierFilter = new MultiSelectComboBox("Tier", tierNames, this::applyFilter);
		this.typeFilter = new MultiSelectComboBox("Type", typeNames, this::applyFilter);

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

		this.searchField.setToolTipText("Search by tier, type, clue text or coordinates (space separates terms)");
		this.searchField.getDocument().addDocumentListener(new DocumentListenerAdapter()
		{
			@Override
			protected void changed()
			{
				applyFilter();
			}
		});
		top.add(this.searchField);

		top.add(Box.createVerticalStrut(10));

		JPanel filters = new JPanel(new GridLayout(2, 1, 0, 4));
		this.tierFilter.setPreferredSize(new Dimension(0, 26));
		this.typeFilter.setPreferredSize(new Dimension(0, 26));
		filters.add(this.tierFilter);
		filters.add(this.typeFilter);
		top.add(filters);

		top.add(Box.createVerticalStrut(10));

		add(top, BorderLayout.NORTH);

		this.clueList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		this.clueList.setCellRenderer(new WrappingTextRenderer());
		this.clueList.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() != 1)
				{
					return;
				}
				int row = CluePickerPanel.this.clueList.locationToIndex(e.getPoint());
				if (row >= 0 && row < CluePickerPanel.this.clueList.getModel().getSize())
				{
					toggleClue(CluePickerPanel.this.clueList.getModel().getElementAt(row));
				}
			}
		});
		this.clueList.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ENTER)
				{
					toggleClue(CluePickerPanel.this.clueList.getSelectedValue());
				}
			}
		});

		add(new JScrollPane(this.clueList), BorderLayout.CENTER);

		JPanel footer = new JPanel(new BorderLayout());
		this.statusLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		this.countLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		footer.add(this.statusLabel, BorderLayout.NORTH);
		footer.add(this.countLabel, BorderLayout.SOUTH);
		add(footer, BorderLayout.SOUTH);

		applyFilter();
		setFakeClue(null);
	}

	@Override
	public Dimension getPreferredSize()
	{
		// The sidebar wraps this panel in a JScrollPane that only sizes the view
		// to its preferred size, so claim the full visible height to let the list stretch.
		Dimension size = super.getPreferredSize();
		size.height = Math.max(size.height, getScrollPane().getHeight());
		return size;
	}

	private void toggleClue(ClueEntry entry)
	{
		if (entry == null)
		{
			return;
		}
		if (entry.getClue() == this.currentFake)
		{
			this.onClear.run();
		}
		else
		{
			this.onSelect.accept(entry.getClue());
		}
	}

	private void applyFilter()
	{
		String query = this.searchField.getText().trim().toLowerCase();
		String[] tokens = query.isEmpty() ? new String[0] : query.split("\\s+");

		int visibleCount = 0;
		this.listModel.clear();
		List<ClueEntry> visible = new ArrayList<>();
		for (ClueEntry entry : this.allClues)
		{
			if (!this.tierFilter.isSelected(entry.getTier()))
			{
				continue;
			}
			if (!this.typeFilter.isSelected(entry.getType()))
			{
				continue;
			}
			if (!entry.matches(tokens))
			{
				continue;
			}
			this.listModel.addElement(entry);
			visible.add(entry);
			visibleCount++;
		}

		this.countLabel.setText(visibleCount + " of " + this.allClues.size() + " clues");
		this.onFilterChange.accept(visible);
		if (this.currentFake != null)
		{
			setFakeClue(this.currentFake);
		}
	}

	public void setFakeClue(ClueScroll clue)
	{
		this.currentFake = clue;
		if (clue == null)
		{
			this.statusLabel.setText("No fake clue");
			this.statusLabel.setToolTipText(null);
			this.clueList.clearSelection();
			return;
		}

		String label = labelOf(clue);
		this.statusLabel.setText("Fake clue: " + label);
		this.statusLabel.setToolTipText("<html><body style='width: 280px'>" + escapeHtml(label) + "</body></html>");
		for (int i = 0; i < this.clueList.getModel().getSize(); i++)
		{
			ClueEntry entry = this.clueList.getModel().getElementAt(i);
			if (entry.getClue() == clue)
			{
				this.clueList.setSelectedIndex(i);
				this.clueList.ensureIndexIsVisible(i);
				return;
			}
		}
		this.clueList.clearSelection();
	}

	private String labelOf(ClueScroll clue)
	{
		for (ClueEntry entry : this.allClues)
		{
			if (entry.getClue() == clue)
			{
				return entry.getLabel();
			}
		}
		return clue.getClass().getSimpleName();
	}

	private static String escapeHtml(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static class WrappingTextRenderer extends JTextArea implements ListCellRenderer<ClueEntry>
	{
		WrappingTextRenderer()
		{
			setLineWrap(true);
			setWrapStyleWord(true);
			setEditable(false);
			setFocusable(false);
			setOpaque(true);
			setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
			setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends ClueEntry> list, ClueEntry value, int index, boolean isSelected, boolean cellHasFocus)
		{
			String label = value == null ? "" : value.getLabel();
			if (value != null && value.getFailure() != null)
			{
				label += "  [FAILED: " + value.getFailure() + "]";
			}
			setText(label);
			if (isSelected)
			{
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			}
			else if (value != null && value.getFailure() != null)
			{
				setBackground(list.getBackground());
				setForeground(Color.RED);
			}
			else
			{
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}
			// Force word-wrap at the list's width so the preferred height reflects wrapped lines.
			int width = list.getWidth() - 8;
			if (width <= 0)
			{
				width = 190;
			}
			setSize(width, Short.MAX_VALUE);
			return this;
		}
	}

	private static class HintTextField extends JTextField
	{
		private final String hint;

		HintTextField(String hint)
		{
			this.hint = hint;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			if (!getText().isEmpty() || hasFocus())
			{
				return;
			}
			g.setColor(Color.GRAY);
			FontMetrics fm = g.getFontMetrics();
			int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
			g.drawString(this.hint, getInsets().left + 4, y);
		}
	}

	private abstract static class DocumentListenerAdapter implements javax.swing.event.DocumentListener
	{
		@Override
		public void insertUpdate(javax.swing.event.DocumentEvent e)
		{
			changed();
		}

		@Override
		public void removeUpdate(javax.swing.event.DocumentEvent e)
		{
			changed();
		}

		@Override
		public void changedUpdate(javax.swing.event.DocumentEvent e)
		{
			changed();
		}

		protected abstract void changed();
	}
}
